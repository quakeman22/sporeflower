package org.jetbrains.java.decompiler.modules.renamer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer;
import org.jetbrains.java.decompiler.main.extern.IVariableNameProvider;
import org.jetbrains.java.decompiler.main.extern.IVariableNamingFactory;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.NewClassNameBuilder;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.Pair;
import org.jetbrains.java.decompiler.util.TextUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class Tiny2IdentifierRenamer implements IIdentifierRenamer {
  private final Map<String, String> classRenames;
  private final Map<MemberKey, String> fieldRenames;
  private final Map<MemberKey, String> methodRenames;
  private final Map<MemberKey, Map<Integer, String>> parameterRenames;
  private final int parameterEntryCount;

  private final Map<String, Integer> classRenameAttempts = new HashMap<>();
  private final Map<MemberKey, Integer> fieldRenameAttempts = new HashMap<>();
  private final Map<MemberKey, Integer> methodRenameAttempts = new HashMap<>();

  private Tiny2IdentifierRenamer(
    Map<String, String> classRenames,
    Map<MemberKey, String> fieldRenames,
    Map<MemberKey, String> methodRenames,
    Map<MemberKey, Map<Integer, String>> parameterRenames,
    int parameterEntryCount
  ) {
    this.classRenames = classRenames;
    this.fieldRenames = fieldRenames;
    this.methodRenames = methodRenames;
    this.parameterRenames = parameterRenames;
    this.parameterEntryCount = parameterEntryCount;
  }

  public static Tiny2IdentifierRenamer fromFile(Path mappingPath, String sourceNamespace, String targetNamespace) throws IOException {
    List<String> lines = Files.readAllLines(mappingPath, StandardCharsets.UTF_8);
    if (lines.isEmpty()) {
      throw new IOException("Tiny mapping file is empty: " + mappingPath);
    }

    Header header = parseHeader(lines.get(0), mappingPath);
    int sourceNamespaceIndex = findNamespaceIndex(header.namespaces(), sourceNamespace, 0, "source", mappingPath);
    int targetNamespaceIndex = findNamespaceIndex(header.namespaces(), targetNamespace, 1, "target", mappingPath);
    if (sourceNamespaceIndex == targetNamespaceIndex) {
      throw new IOException("Tiny mapping source and target namespaces resolve to the same index (" + sourceNamespaceIndex + ") in " + mappingPath);
    }

    Map<String, String> classRenames = new LinkedHashMap<>();
    for (int lineNo = 2; lineNo <= lines.size(); lineNo++) {
      String line = lines.get(lineNo - 1);
      int indent = countLeadingTabs(line);
      String body = line.substring(indent);
      if (indent != 0 || body.isBlank() || body.startsWith("#")) {
        continue;
      }

      String[] columns = body.split("\t", -1);
      if (!"c".equals(columns[0])) {
        continue;
      }

      ensureColumns(columns, 1 + header.namespaces().size(), mappingPath, lineNo, "class");
      String fromName = unescape(columns[1 + sourceNamespaceIndex]);
      String toName = unescape(columns[1 + targetNamespaceIndex]);
      addRename(classRenames, fromName, fromName, toName, mappingPath, lineNo, "class");
    }

    Map<MemberKey, String> fieldRenames = new LinkedHashMap<>();
    Map<MemberKey, String> methodRenames = new LinkedHashMap<>();
    Map<MemberKey, Map<Integer, String>> parameterRenames = new LinkedHashMap<>();
    int parameterEntryCount = 0;

    String currentClass = null;
    MemberKey currentMethodSource = null;
    List<MemberKey> currentMethodParameterKeys = Collections.emptyList();

    for (int lineNo = 2; lineNo <= lines.size(); lineNo++) {
      String line = lines.get(lineNo - 1);
      int indent = countLeadingTabs(line);
      String body = line.substring(indent);
      if (body.isBlank() || body.startsWith("#")) {
        continue;
      }

      String[] columns = body.split("\t", -1);
      String kind = columns[0];

      if (indent == 0) {
        currentMethodSource = null;
        currentMethodParameterKeys = Collections.emptyList();

        if (!"c".equals(kind)) {
          currentClass = null;
          continue;
        }

        ensureColumns(columns, 1 + header.namespaces().size(), mappingPath, lineNo, "class");
        String fromName = unescape(columns[1 + sourceNamespaceIndex]);
        currentClass = fromName.isEmpty() ? null : fromName;
        continue;
      }

      if (indent == 1) {
        currentMethodSource = null;
        currentMethodParameterKeys = Collections.emptyList();

        if (currentClass == null) {
          continue;
        }

        if ("f".equals(kind)) {
          ensureColumns(columns, 2 + header.namespaces().size(), mappingPath, lineNo, "field");
          String descriptor = unescape(columns[1]);
          String fromName = unescape(columns[2 + sourceNamespaceIndex]);
          String toName = unescape(columns[2 + targetNamespaceIndex]);
          addRename(fieldRenames, new MemberKey(currentClass, fromName, descriptor), fromName, toName, mappingPath, lineNo, "field");
        }
        else if ("m".equals(kind)) {
          ensureColumns(columns, 2 + header.namespaces().size(), mappingPath, lineNo, "method");
          String descriptor = unescape(columns[1]);
          String fromName = unescape(columns[2 + sourceNamespaceIndex]);
          String toName = unescape(columns[2 + targetNamespaceIndex]);

          currentMethodSource = new MemberKey(currentClass, fromName, descriptor);
          String mappedOwner = classRenames.getOrDefault(currentClass, currentClass);
          String mappedDescriptor = remapMethodDescriptor(descriptor, classRenames);
          MemberKey currentMethodTarget = new MemberKey(mappedOwner, toName, mappedDescriptor);
          currentMethodParameterKeys = buildParameterMethodKeys(currentMethodSource, currentMethodTarget);

          addRename(methodRenames, currentMethodSource, fromName, toName, mappingPath, lineNo, "method");
        }

        continue;
      }

      if (indent == 2 && "p".equals(kind) && currentMethodSource != null) {
        ensureColumns(columns, 2 + header.namespaces().size(), mappingPath, lineNo, "parameter");

        final int lvIndex;
        try {
          lvIndex = Integer.parseInt(columns[1]);
        }
        catch (NumberFormatException ex) {
          throw new IOException("Invalid Tiny parameter local variable index at " + mappingPath + ":" + lineNo + ": '" + columns[1] + "'", ex);
        }

        String fromName = unescape(columns[2 + sourceNamespaceIndex]);
        String toName = unescape(columns[2 + targetNamespaceIndex]);
        boolean added = addParameterRename(parameterRenames, currentMethodSource, lvIndex, fromName, toName, mappingPath, lineNo);
        for (MemberKey key : currentMethodParameterKeys) {
          if (!key.equals(currentMethodSource)) {
            addParameterRename(parameterRenames, key, lvIndex, fromName, toName, mappingPath, lineNo);
          }
        }
        if (added) {
          parameterEntryCount++;
        }
      }
    }

    return new Tiny2IdentifierRenamer(
      Collections.unmodifiableMap(classRenames),
      Collections.unmodifiableMap(fieldRenames),
      Collections.unmodifiableMap(methodRenames),
      freezeParameterMap(parameterRenames),
      parameterEntryCount
    );
  }

  public int classRenameCount() {
    return classRenames.size();
  }

  public int fieldRenameCount() {
    return fieldRenames.size();
  }

  public int methodRenameCount() {
    return methodRenames.size();
  }

  public int parameterRenameCount() {
    return parameterEntryCount;
  }

  public String getParameterRename(String owner, String methodName, String descriptor, int localVariableIndex) {
    Map<Integer, String> methodParams = parameterRenames.get(new MemberKey(owner, methodName, descriptor));
    if (methodParams == null) {
      return null;
    }
    return methodParams.get(localVariableIndex);
  }

  public IVariableNamingFactory createVariableNamingFactory(IVariableNamingFactory delegateFactory) {
    return new Tiny2ParameterNameFactory(parameterRenames, delegateFactory);
  }

  @Override
  public boolean toBeRenamed(Type elementType, String className, String element, String descriptor) {
    if (className == null) {
      return false;
    }

    return switch (elementType) {
      case ELEMENT_CLASS -> classRenames.containsKey(className);
      case ELEMENT_FIELD -> element != null && descriptor != null && fieldRenames.containsKey(new MemberKey(className, element, descriptor));
      case ELEMENT_METHOD -> element != null
        && descriptor != null
        && !CodeConstants.INIT_NAME.equals(element)
        && !CodeConstants.CLINIT_NAME.equals(element)
        && methodRenames.containsKey(new MemberKey(className, element, descriptor));
    };
  }

  @Override
  public String getNextClassName(String fullName, String shortName) {
    String mapped = classRenames.get(fullName);
    if (mapped == null) {
      return shortName != null ? shortName : fullName;
    }
    return withRetrySuffix(mapped, classRenameAttempts.merge(fullName, 1, Integer::sum));
  }

  @Override
  public String getNextFieldName(String className, String field, String descriptor) {
    MemberKey key = new MemberKey(className, field, descriptor);
    String mapped = fieldRenames.get(key);
    if (mapped == null) {
      return field;
    }
    return withRetrySuffix(mapped, fieldRenameAttempts.merge(key, 1, Integer::sum));
  }

  @Override
  public String getNextMethodName(String className, String method, String descriptor) {
    MemberKey key = new MemberKey(className, method, descriptor);
    String mapped = methodRenames.get(key);
    if (mapped == null) {
      return method;
    }
    return withRetrySuffix(mapped, methodRenameAttempts.merge(key, 1, Integer::sum));
  }

  private static String withRetrySuffix(String base, int attempt) {
    if (attempt <= 1) {
      return base;
    }
    return base + "_" + (attempt - 1);
  }

  private static Header parseHeader(String line, Path mappingPath) throws IOException {
    String normalized = line.stripLeading();
    if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
      normalized = normalized.substring(1);
    }

    String[] columns = normalized.split("\t", -1);
    if (columns.length < 5 || !"tiny".equals(columns[0]) || !"2".equals(columns[1])) {
      throw new IOException("Unsupported Tiny header in " + mappingPath + ": expected Tiny v2");
    }

    String[] namespaces = Arrays.copyOfRange(columns, 3, columns.length);
    if (namespaces.length < 2) {
      throw new IOException("Tiny mapping requires at least two namespaces: " + mappingPath);
    }

    return new Header(Arrays.asList(namespaces));
  }

  private static int findNamespaceIndex(List<String> namespaces, String requestedNamespace, int fallback, String role, Path mappingPath) throws IOException {
    String requested = requestedNamespace == null ? "" : requestedNamespace.trim();
    if (!requested.isEmpty()) {
      int index = namespaces.indexOf(requested);
      if (index < 0) {
        throw new IOException("Unknown Tiny " + role + " namespace '" + requested + "' in " + mappingPath + ". Available: " + namespaces);
      }
      return index;
    }

    if (fallback >= namespaces.size()) {
      throw new IOException("Tiny mapping in " + mappingPath + " does not provide enough namespaces for the default " + role + " namespace");
    }

    return fallback;
  }

  private static int countLeadingTabs(String line) {
    int tabs = 0;
    while (tabs < line.length() && line.charAt(tabs) == '\t') {
      tabs++;
    }
    return tabs;
  }

  private static void ensureColumns(String[] columns, int minimum, Path mappingPath, int lineNo, String itemType) throws IOException {
    if (columns.length < minimum) {
      throw new IOException("Invalid Tiny " + itemType + " entry at " + mappingPath + ":" + lineNo + ": expected at least " + minimum + " columns");
    }
  }

  private static <K> void addRename(
    Map<K, String> map,
    K key,
    String sourceName,
    String targetName,
    Path mappingPath,
    int lineNo,
    String itemType
  ) throws IOException {
    if (targetName == null || targetName.isEmpty() || key == null || sourceName == null || sourceName.isEmpty() || sourceName.equals(targetName)) {
      return;
    }

    String existing = map.putIfAbsent(key, targetName);
    if (existing != null && !existing.equals(targetName)) {
      throw new IOException(
        "Conflicting Tiny " + itemType + " rename for " + key + " at " + mappingPath + ":" + lineNo
          + ": existing '" + existing + "', new '" + targetName + "'"
      );
    }
  }

  private static boolean addParameterRename(
    Map<MemberKey, Map<Integer, String>> map,
    MemberKey methodKey,
    int localVariableIndex,
    String sourceName,
    String targetName,
    Path mappingPath,
    int lineNo
  ) throws IOException {
    if (methodKey == null || targetName == null || targetName.isEmpty() || sourceName == null || sourceName.equals(targetName)) {
      return false;
    }

    Map<Integer, String> namesByIndex = map.computeIfAbsent(methodKey, k -> new LinkedHashMap<>());
    String existing = namesByIndex.putIfAbsent(localVariableIndex, targetName);
    if (existing != null && !existing.equals(targetName)) {
      throw new IOException(
        "Conflicting Tiny parameter rename for " + methodKey + " lv=" + localVariableIndex + " at " + mappingPath + ":" + lineNo
          + ": existing '" + existing + "', new '" + targetName + "'"
      );
    }
    return existing == null;
  }

  private static Map<MemberKey, Map<Integer, String>> freezeParameterMap(Map<MemberKey, Map<Integer, String>> map) {
    Map<MemberKey, Map<Integer, String>> frozen = new LinkedHashMap<>();
    for (Map.Entry<MemberKey, Map<Integer, String>> entry : map.entrySet()) {
      frozen.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
    }
    return Collections.unmodifiableMap(frozen);
  }

  private static String remapMethodDescriptor(String descriptor, Map<String, String> classMap) {
    try {
      MethodDescriptor parsed = MethodDescriptor.parseDescriptor(descriptor);
      NewClassNameBuilder builder = classMap::get;
      String rebuilt = parsed.buildNewDescriptor(builder);
      return rebuilt != null ? rebuilt : descriptor;
    }
    catch (RuntimeException ex) {
      return descriptor;
    }
  }

  private static List<MemberKey> buildParameterMethodKeys(MemberKey source, MemberKey target) {
    LinkedHashSet<String> owners = new LinkedHashSet<>();
    LinkedHashSet<String> names = new LinkedHashSet<>();
    LinkedHashSet<String> descriptors = new LinkedHashSet<>();

    addOwnerWithDefpackageNormalization(owners, source.owner());
    addOwnerWithDefpackageNormalization(owners, target.owner());
    names.add(source.name());
    names.add(target.name());
    addDescriptorWithDefpackageNormalization(descriptors, source.descriptor());
    addDescriptorWithDefpackageNormalization(descriptors, target.descriptor());

    LinkedHashSet<MemberKey> keys = new LinkedHashSet<>();
    for (String owner : owners) {
      for (String name : names) {
        for (String descriptor : descriptors) {
          keys.add(new MemberKey(owner, name, descriptor));
        }
      }
    }

    return List.copyOf(keys);
  }

  private static MemberKey normalizeDefpackageMethodKey(MemberKey key) {
    if (key == null) {
      return null;
    }

    String owner = stripDefpackageOwner(key.owner());
    String descriptor = stripDefpackageDescriptor(key.descriptor());
    if (owner.equals(key.owner()) && descriptor.equals(key.descriptor())) {
      return key;
    }
    return new MemberKey(owner, key.name(), descriptor);
  }

  private static void addOwnerWithDefpackageNormalization(Set<String> sink, String value) {
    sink.add(value);
    String normalized = stripDefpackageOwner(value);
    if (!normalized.equals(value)) {
      sink.add(normalized);
    }
  }

  private static void addDescriptorWithDefpackageNormalization(Set<String> sink, String value) {
    sink.add(value);
    String normalized = stripDefpackageDescriptor(value);
    if (!normalized.equals(value)) {
      sink.add(normalized);
    }
  }

  private static String stripDefpackageOwner(String owner) {
    if (owner == null || owner.isEmpty() || !owner.startsWith("defpackage/")) {
      return owner;
    }
    return owner.substring("defpackage/".length());
  }

  private static String stripDefpackageDescriptor(String descriptor) {
    if (descriptor == null || descriptor.isEmpty() || descriptor.indexOf("defpackage/") < 0) {
      return descriptor;
    }

    StringBuilder out = new StringBuilder(descriptor.length());
    int index = 0;
    while (index < descriptor.length()) {
      char ch = descriptor.charAt(index);
      if (ch != 'L') {
        out.append(ch);
        index++;
        continue;
      }

      int semicolon = descriptor.indexOf(';', index);
      if (semicolon < 0) {
        return descriptor;
      }

      String typeName = descriptor.substring(index + 1, semicolon);
      typeName = stripDefpackageOwner(typeName);
      out.append('L').append(typeName).append(';');
      index = semicolon + 1;
    }

    return out.toString();
  }

  private static String unescape(String value) {
    if (value.indexOf('\\') < 0) {
      return value;
    }

    StringBuilder sb = new StringBuilder(value.length());
    boolean escaped = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!escaped) {
        if (c == '\\') {
          escaped = true;
        }
        else {
          sb.append(c);
        }
      }
      else {
        switch (c) {
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case '0' -> sb.append('\0');
          case '\\' -> sb.append('\\');
          default -> sb.append(c);
        }
        escaped = false;
      }
    }

    if (escaped) {
      sb.append('\\');
    }

    return sb.toString();
  }

  private static String uniqueName(String base, Set<String> used) {
    if (used.add(base)) {
      return base;
    }

    int suffix = 1;
    while (true) {
      String candidate = base + "_" + suffix;
      if (used.add(candidate)) {
        return candidate;
      }
      suffix++;
    }
  }

  private record Header(List<String> namespaces) { }

  private record MemberKey(String owner, String name, String descriptor) {
    @Override
    public String toString() {
      return owner + "." + name + descriptor;
    }
  }

  private static final class Tiny2ParameterNameFactory implements IVariableNamingFactory {
    private final Map<MemberKey, Map<Integer, String>> parameterRenames;
    private final IVariableNamingFactory delegateFactory;

    private Tiny2ParameterNameFactory(Map<MemberKey, Map<Integer, String>> parameterRenames, IVariableNamingFactory delegateFactory) {
      this.parameterRenames = parameterRenames;
      this.delegateFactory = delegateFactory;
    }

    @Override
    public @NotNull IVariableNameProvider createFactory(StructMethod structMethod) {
      Map<Integer, String> names = findParameterRenames(structMethod.getClassQualifiedName(), structMethod.getName(), structMethod.getDescriptor());
      IVariableNameProvider delegate = delegateFactory != null ? delegateFactory.createFactory(structMethod) : null;
      return new Tiny2ParameterNameProvider(structMethod, names, delegate);
    }

    private Map<Integer, String> findParameterRenames(String owner, String name, String descriptor) {
      MemberKey exact = new MemberKey(owner, name, descriptor);
      Map<Integer, String> names = parameterRenames.get(exact);
      if (names != null) {
        return names;
      }

      MemberKey normalized = normalizeDefpackageMethodKey(exact);
      if (!normalized.equals(exact)) {
        names = parameterRenames.get(normalized);
        if (names != null) {
          return names;
        }
      }

      return Collections.emptyMap();
    }
  }

  private static final class Tiny2ParameterNameProvider implements IVariableNameProvider {
    private final StructMethod method;
    private final Map<Integer, String> parameterNames;
    private final IVariableNameProvider delegate;

    private Tiny2ParameterNameProvider(StructMethod method, Map<Integer, String> parameterNames, IVariableNameProvider delegate) {
      this.method = method;
      this.parameterNames = normalizeParameterNames(parameterNames);
      this.delegate = delegate;
    }

    @Override
    public Map<VarVersionPair, String> rename(Map<VarVersionPair, Pair<VarType, String>> variables) {
      Map<VarVersionPair, String> delegateRenames = delegate != null ? delegate.rename(variables) : null;
      if (parameterNames.isEmpty()) {
        return delegateRenames;
      }

      Map<VarVersionPair, String> result = new LinkedHashMap<>();
      if (delegateRenames != null) {
        result.putAll(delegateRenames);
      }

      Set<String> used = new HashSet<>(result.values());
      Map<Integer, String> assigned = new HashMap<>();
      List<VarVersionPair> keys = new ArrayList<>(variables.keySet());
      keys.sort(Comparator.comparingInt((VarVersionPair v) -> v.var).thenComparingInt(v -> v.version));

      for (VarVersionPair key : keys) {
        String base = parameterNames.get(key.var);
        if (base == null) {
          continue;
        }

        String name = assigned.get(key.var);
        if (name == null) {
          name = uniqueName(base, used);
          assigned.put(key.var, name);
        }

        result.put(key, name);
      }

      return result;
    }

    @Override
    public String renameAbstractParameter(String name, int index) {
      String mapped = parameterNames.get(index);
      if (mapped != null) {
        return mapped;
      }
      if (delegate != null) {
        return delegate.renameAbstractParameter(name, index);
      }
      return IVariableNameProvider.super.renameAbstractParameter(name, index);
    }

    @Override
    public String renameParameter(int flags, VarType type, String name, int index) {
      String mapped = parameterNames.get(index);
      if (mapped != null) {
        return mapped;
      }
      if (delegate != null) {
        return delegate.renameParameter(flags, type, name, index);
      }
      return IVariableNameProvider.super.renameParameter(flags, type, name, index);
    }

    @Override
    public void addParentContext(IVariableNameProvider renamer) {
      if (delegate == null) {
        return;
      }

      if (renamer instanceof Tiny2ParameterNameProvider tinyParent && tinyParent.delegate != null) {
        delegate.addParentContext(tinyParent.delegate);
      }
      else {
        delegate.addParentContext(renamer);
      }
    }

    private Map<Integer, String> normalizeParameterNames(Map<Integer, String> rawNames) {
      if (rawNames.isEmpty()) {
        return Collections.emptyMap();
      }

      Map<Integer, String> normalized = new LinkedHashMap<>();
      Set<String> used = new HashSet<>();
      List<Integer> indices = new ArrayList<>(rawNames.keySet());
      Collections.sort(indices);

      for (Integer index : indices) {
        String name = sanitizeIdentifier(rawNames.get(index), index);
        name = uniqueName(name, used);
        normalized.put(index, name);
      }

      return Collections.unmodifiableMap(normalized);
    }

    private String sanitizeIdentifier(String rawName, int index) {
      if (rawName == null || rawName.isEmpty()) {
        return "param" + index;
      }

      StringBuilder sb = new StringBuilder(rawName.length() + 1);
      char first = rawName.charAt(0);
      if (!Character.isJavaIdentifierStart(first)) {
        sb.append('_');
      }

      for (int i = 0; i < rawName.length(); i++) {
        char c = rawName.charAt(i);
        sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
      }

      String candidate = sb.toString();
      if (candidate.isEmpty()) {
        candidate = "param" + index;
      }

      if (!TextUtil.isValidIdentifier(candidate, method.getBytecodeVersion(), method)) {
        if (TextUtil.isKeyword(candidate, method.getBytecodeVersion(), method)) {
          candidate = candidate + "_";
        }
        if (!TextUtil.isValidIdentifier(candidate, method.getBytecodeVersion(), method)) {
          candidate = "param" + index;
        }
      }

      return candidate;
    }
  }
}
