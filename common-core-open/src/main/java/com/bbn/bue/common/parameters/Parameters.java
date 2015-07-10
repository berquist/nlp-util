package com.bbn.bue.common.parameters;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.converters.StrictStringToBoolean;
import com.bbn.bue.common.converters.StringConverter;
import com.bbn.bue.common.converters.StringToDouble;
import com.bbn.bue.common.converters.StringToEnum;
import com.bbn.bue.common.converters.StringToFile;
import com.bbn.bue.common.converters.StringToInteger;
import com.bbn.bue.common.converters.StringToOSFile;
import com.bbn.bue.common.converters.StringToStringList;
import com.bbn.bue.common.converters.StringToStringSet;
import com.bbn.bue.common.converters.StringToSymbolList;
import com.bbn.bue.common.converters.StringToSymbolSet;
import com.bbn.bue.common.parameters.exceptions.InvalidEnumeratedPropertyException;
import com.bbn.bue.common.parameters.exceptions.MissingRequiredParameter;
import com.bbn.bue.common.parameters.exceptions.ParameterConversionException;
import com.bbn.bue.common.parameters.exceptions.ParameterException;
import com.bbn.bue.common.parameters.exceptions.ParameterValidationException;
import com.bbn.bue.common.parameters.serifstyle.SerifStyleParameterFileLoader;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.validators.AlwaysValid;
import com.bbn.bue.common.validators.And;
import com.bbn.bue.common.validators.FileExists;
import com.bbn.bue.common.validators.IsDirectory;
import com.bbn.bue.common.validators.IsFile;
import com.bbn.bue.common.validators.IsInRange;
import com.bbn.bue.common.validators.IsNonNegative;
import com.bbn.bue.common.validators.IsPositive;
import com.bbn.bue.common.validators.ValidationException;
import com.bbn.bue.common.validators.Validator;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Represents a set of parameters passed into a program.  The parameters are assumed to originate as
 * key-value pairs of <code>String</code>s, which can then be accessed in various validated ways.
 * This class is immutable. Keys will never be null or empty. Values will never be null.
 *
 * For all methods to get parameters, looking up a missing parameter throws an unchecked {@link
 * MissingRequiredParameter} exception.
 *
 * @author rgabbard
 */
@Beta
public final class Parameters {

  public static final String DO_OS_CONVERSION_PARAM = "os_filepath_conversion";

  /**
   * Constructs a Parameters object from a <code>Map</code>.  The Map may contain neither null keys,
   * empty keys, or null values.
   *
   * @deprecated Prefer fromMap()
   */
  @Deprecated
  public Parameters(final Map<String, String> params) {
    this(params, ImmutableList.<String>of());
  }

  private Parameters(final Map<String, String> params, final List<String> namespace) {
    this.namespace = ImmutableList.copyOf(namespace);
    this.params = ImmutableMap.copyOf(params);
    for (final Map.Entry<String, String> param : params.entrySet()) {
      checkNotNull(param.getKey());
      checkNotNull(param.getValue());
      checkArgument(!param.getKey().isEmpty());
    }
  }

  /**
   * Creates a new set of parameters with only those parameters in the specified namespace (that is,
   * prefixed by "namespace.". The namespace prefix and period will be removed from parameter names
   * in the new {@code Parameters}.
   */
  public Parameters copyNamespace(final String requestedNamespace) {
    checkArgument(!requestedNamespace.isEmpty());
    final ImmutableMap.Builder<String, String> ret = ImmutableMap.builder();
    final String dottedNamespace = requestedNamespace + ".";
    for (final Map.Entry<String, String> param : params.entrySet()) {
      if (param.getKey().startsWith(dottedNamespace)) {
        ret.put(param.getKey().substring(dottedNamespace.length()), param.getValue());
      }
    }
    final List<String> newNamespace = Lists.newArrayList();
    newNamespace.addAll(namespace);
    newNamespace.add(requestedNamespace);
    return new Parameters(ret.build(), newNamespace);
  }

  /**
   * If the specified namespace is present, return a copy of that namespace as a parameter set.
   * Otherwise, return a copy of this parameter set.
   */
  public Parameters copyNamespaceIfPresent(final String requestedNamespace) {
    if (isNamespacePresent(requestedNamespace)) {
      return copyNamespace(requestedNamespace);
    } else {
      return copy();
    }
  }

  /**
   * Returns if any parameter in this parameter set begins the the specified string, followed by a
   * dot. The argument may not be empty.
   */
  public boolean isNamespacePresent(final String requestedNamespace) {
    checkArgument(requestedNamespace.length() > 0);
    final String probe = requestedNamespace + ".";
    return Iterables.any(params.keySet(), StringUtils.startsWith(probe));
  }

  /**
   * Creates a copy of this parameter set.
   */
  public Parameters copy() {
    return new Parameters(params, namespace);
  }

  public String dump() {
    return dump(true, true);
  }

  public String dumpWithoutNamespacePrefix() {
    return dump(true, false);
  }

  public String dump(final boolean printDateTime) {
    return dump(printDateTime, true);
  }

  /**
   * Dumps the parameters object as colon-separated key-value pairs. If {@code printDateTime} is
   * true, will put a #-style comment with the current date and time at the top. If
   * includeNamespacePrefix is true, will prefix its parameter with its full namespace instead of
   * writing all keys relative to the current namespace.
   */
  public String dump(final boolean printDateTime, final boolean includeNamespacePrefix) {
    final StringWriter sOut = new StringWriter();
    final PrintWriter out = new PrintWriter(sOut);

    if (printDateTime) {
      // output a timestamp comment
      final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      out.format("#%s\n", timeFormat.format(new Date()));
    }
    List<String> keys = new ArrayList<String>(params.keySet());
    Collections.sort(keys);
    for (final String rawKey : keys) {
      final String key;
      if (includeNamespacePrefix) {
        key = fullString(rawKey);
      } else {
        key = rawKey;
      }
      out.format("%s: %s\n", key, params.get(rawKey));
    }

    out.close();
    return sOut.toString();
  }


  public static Parameters loadSerifStyle(final File f) throws IOException {
    final SerifStyleParameterFileLoader loader = new SerifStyleParameterFileLoader();
    return new Parameters(loader.load(f));
  }

  public static Parameters fromMap(Map<String, String> map) {
    return new Parameters(map);
  }

  /**
   * Creates a {@code Parameters} from a {@link java.util.Properties} by turning each key and value
   * in the {@code Properties} into a string. If multiple keys in the properties object have the
   * same string representation or if any key or value is null, an {@link
   * java.lang.IllegalArgumentException} or {@link java.lang.NullPointerException} will be thrown.
   */
  public static Parameters fromProperties(Properties properties) {
    final ImmutableMap.Builder<String, String> ret = ImmutableMap.builder();
    for (final Map.Entry<Object, Object> property : properties.entrySet()) {
      ret.put(property.getKey().toString(), property.getValue().toString());
    }
    return fromMap(ret.build());
  }

  /**
   * Combines these parameters with the others supplied to make a new <code>Parameters</code>. The new parameters
   * will contain all mappings present in either. If a mapping is present in both, the <code>other</code> argument
   * parameters take precedence.
   */
  // this is currently unused anywhere, and it will require a little
  // thought how best to make it interact with namespacing
        /*public Parameters compose(final Parameters other) {
                checkNotNull(other);
		final Map<String, String> newMap = Maps.newHashMap();
		newMap.putAll(params);
		newMap.putAll(other.params);
		return new Parameters(newMap);
	}*/

  /**
   * Returns true iff the key <code>param</code> is assigned a value.
   */
  public boolean isPresent(final String param) {
    return params.containsKey(checkNotNull(param));
  }

  /**
   * Gets the value for a parameter as a raw string.
   */
  public String getString(final String param) {
    checkNotNull(param);
    checkArgument(!param.isEmpty());

    final String ret = params.get(param);

    if (ret != null) {
      return ret;
    } else {
      throw new MissingRequiredParameter(fullString(param));
    }
  }

  private String fullString(final String param) {
    if (namespace.isEmpty()) {
      return param;
    } else {
      return StringUtils.DotJoiner.join(namespace) + "." + param;
    }
  }

  /**
   * Gets the parameter string for the key <code>param</code>, then runs it throught the converter
   * and checks it with the validator.
   *
   * @param expectation What we expected to see, for produceing error messages.  e.g. "integer" or
   *                    "comma-separated list of strings"
   */
  public <T> T get(final String param, final StringConverter<T> converter,
      final Validator<T> validator,
      final String expectation) {
    checkNotNull(param);
    checkNotNull(converter);
    checkNotNull(validator);
    checkNotNull(expectation);

    final String value = getString(param);

    T ret;

    try {
      ret = converter.decode(value);
    } catch (final Exception e) {
      throw new ParameterConversionException(fullString(param), value, e, expectation);
    }

    try {
      validator.validate(ret);
    } catch (final ValidationException e) {
      throw new ParameterValidationException(fullString(param), value, e);
    }

    if (ret == null) {
      throw new RuntimeException(
          "Parameter converters not allowed to return null for non-null input.");
    }

    return ret;
  }

  /**
   * Gets the parameter string *list* for the key <code>param</code>, then runs each element
   * throught the converter and checks it with the validator.
   *
   * @param expectation What we expected to see, for produceing error messages.  e.g. "integer" or
   *                    "comma-separated list of strings"
   */
  public <T> List<T> getList(final String param, final StringConverter<T> converter,
      final Validator<T> validator,
      final String expectation) {
    checkNotNull(param);
    checkNotNull(converter);
    checkNotNull(validator);
    checkNotNull(expectation);

    final List<String> values = getStringList(param);

    final ImmutableList.Builder<T> retList = ImmutableList.builder();

    for (final String value : values) {
      T ret;

      try {
        ret = converter.decode(value);
      } catch (final Exception e) {
        throw new ParameterConversionException(fullString(param), value, e,
            expectation);
      }

      try {
        validator.validate(ret);
      } catch (final ValidationException e) {
        throw new ParameterValidationException(fullString(param), value, e);
      }

      if (ret == null) {
        throw new RuntimeException(
            "Parameter converters not allowed to return null for non-null input.");
      }

      retList.add(ret);
    }
    return retList.build();
  }

  /**
   * Looks up a parameter.  If the value is not in <code>possibleValues</code>, throws and
   * exception.
   *
   * @param possibleValues May not be null. May not be empty.
   * @throws InvalidEnumeratedPropertyException if the parameter value is not on the list.
   */
  public String getStringOf(final String param, final List<String> possibleValues) {
    checkNotNull(possibleValues);
    checkArgument(!possibleValues.isEmpty());

    final String value = getString(param);

    if (possibleValues.contains(value)) {
      return value;
    } else {
      throw new InvalidEnumeratedPropertyException(fullString(param), value, possibleValues);
    }
  }

  /**
   * Looks up a parameter, then uses the value as a key in a map lookup.  If the value is not a key
   * in the map, throws an exception.
   *
   * @param possibleValues May not be null. May not be empty.
   * @throws InvalidEnumeratedPropertyException if the parameter value is not on the list.
   */
  public <T> T getMapped(final String param, final Map<String, T> possibleValues) {
    checkNotNull(possibleValues);
    checkArgument(!possibleValues.isEmpty());

    final String value = getString(param);
    final T ret = possibleValues.get(value);

    if (ret == null) {
      throw new InvalidEnumeratedPropertyException(fullString(param), value,
          possibleValues.keySet());
    }
    return ret;
  }

  public <T extends Enum<T>> T getEnum(final String param, final Class<T> clazz) {
    return this.<T>get(param, new StringToEnum<T>(clazz), new AlwaysValid<T>(), "enumeration");
  }

  public <T extends Enum<T>> List<T> getEnumList(final String param, final Class<T> clazz) {
    return this.<T>getList(param, new StringToEnum<T>(clazz), new AlwaysValid<T>(), "enumeration");
  }

  public Class<?> getClassObjectForString(final String className) throws ClassNotFoundException {
    return Class.forName(className);
  }

  public Class<?> getClassObject(final String param) {
    final String className = getString(param);
    try {
      return getClassObjectForString(className);
    } catch (final ClassNotFoundException e) {
      throw new ParameterConversionException(fullString(param), className, e, "class");
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T getParameterInitializedObject(final String param, final Class<T> superClass) {
    final Class<?> clazz = getClassObject(param);

    return parameterInitializedObjectForClass(clazz, param, superClass);
  }

  public <T> Optional<T> getOptionalParameterInitializedObject(final String param,
      final Class<T> superClass) {
    if (isPresent(param)) {
      return Optional.of(getParameterInitializedObject(param, superClass));
    } else {
      return Optional.absent();
    }
  }


  private <T> T parameterInitializedObjectForClass(final Class<?> clazz,
      final String param, final Class<T> superClass) {
    Object ret;

    try {
      ret = createViaConstructor(clazz, param);
    } catch (NoSuchMethodException nsme) {
      try {
        ret = createViaStaticFactoryMethod(clazz, param);
      } catch (NoSuchMethodException nsme2) {
        throw new ParameterValidationException(fullString(param), getString(param),
            new RuntimeException(String.format("Class %s has neither fromParameters(params) "
                    + "static factory method or constructor which takes params",
                clazz.getName())));
      }
    }

    if (superClass.isInstance(ret)) {
      return (T) ret;
    } else {
      throw new ParameterValidationException(fullString(param), getString(param),
          new RuntimeException(
              String.format("Can't cast %s to %s", clazz.getName(), superClass.getName())));
    }
  }

  private Object createViaConstructor(Class<?> clazz, String param) throws NoSuchMethodException {
    try {
      return clazz.getConstructor(Parameters.class).newInstance(this);
    } catch (final IllegalArgumentException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    } catch (final InstantiationException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    } catch (final IllegalAccessException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    } catch (final InvocationTargetException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    }
  }

  private Object createViaStaticFactoryMethod(Class<?> clazz, String param)
      throws NoSuchMethodException {
    try {
      return clazz.getMethod("fromParameters", Parameters.class).invoke(null, this);
    } catch (IllegalAccessException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    } catch (InvocationTargetException e) {
      throw new ParameterValidationException(fullString(param), getString(param), e);
    }
  }

  @SuppressWarnings("unchecked")
  public <T, S> ImmutableList<T> getParameterInitializedObjects(
      final String param, final Class<S> superClass) {
    final List<String> classNames = getStringList(param);
    final ImmutableList.Builder<T> ret = ImmutableList.builder();

    for (final String className : classNames) {
      Class<?> clazz;
      try {
        clazz = getClassObjectForString(className);
      } catch (final ClassNotFoundException e) {
        throw new ParameterValidationException(fullString(param), getString(param), e);
      }

      ret.add((T) parameterInitializedObjectForClass(clazz, param, superClass));
    }

    return ret.build();
  }

  public List<Integer> getIntegerList(final String param) {
    final List<String> intStrings = getStringList(param);
    final ImmutableList.Builder<Integer> ret = ImmutableList.builder();

    for (final String intString : intStrings) {
      try {
        ret.add(Integer.parseInt(intString));
      } catch (final NumberFormatException nfe) {
        throw new ParameterValidationException(fullString(param), intString, nfe);
      }
    }

    return ret.build();
  }

  /**
   * Gets a "true/false" parameter.
   */
  public boolean getBoolean(final String param) {
    return get(param, new StrictStringToBoolean(),
        new AlwaysValid<Boolean>(), "boolean");
  }

  public Optional<Boolean> getOptionalBoolean(final String param) {
    if (isPresent(param)) {
      return Optional.of(getBoolean(param));
    } else {
      return Optional.absent();
    }
  }

  public Optional<String> getOptionalString(final String param) {
    if (isPresent(param)) {
      return Optional.of(getString(param));
    } else {
      return Optional.absent();
    }
  }


  /**
   * Gets an integer parameter.
   */
  public int getInteger(final String param) {
    return get(param, new StringToInteger(),
        new AlwaysValid<Integer>(), "integer");
  }

  public Optional<Integer> getOptionalInteger(final String param) {
    if (isPresent(param)) {
      return Optional.of(getInteger(param));
    } else {
      return Optional.absent();
    }
  }

  /**
   * Gets an positive integer parameter.
   */
  public int getPositiveInteger(final String param) {
    return get(param, new StringToInteger(),
        new IsPositive<Integer>(), "positive integer");
  }


  /**
   * Gets an positive integer list parameter.
   */
  public List<Integer> getPositiveIntegerList(final String param) {
    return getList(param, new StringToInteger(),
        new IsPositive<Integer>(), "positive integer");
  }


  /**
   * Gets a positive double parameter.
   */
  public double getPositiveDouble(final String param) {
    return get(param, new StringToDouble(),
        new IsPositive<Double>(), "positive double");
  }

  public Optional<Double> getOptionalPositiveDouble(final String param) {
    if (isPresent(param)) {
      return Optional.of(getPositiveDouble(param));
    }
    return Optional.absent();
  }

  /**
   * Gets a parameter whose value is a list of positive doubles.
   */
  public List<Double> getPositiveDoubleList(final String param) {
    return getList(param, new StringToDouble(),
        new IsPositive<Double>(), "positive double");
  }

  /**
   * Gets a non-negative double parameter.
   */
  public double getNonNegativeDouble(final String param) {
    return get(param, new StringToDouble(),
        new IsNonNegative<Double>(), "non-negative double");
  }

  /**
   * Gets a parameter whose value is a list of non-negative doubles.
   */
  public List<Double> getNonNegativeDoubleList(final String param) {
    return getList(param, new StringToDouble(),
        new IsNonNegative<Double>(), "non-negative double");
  }

  /**
   * Gets a non-negative integer number parameter.
   */
  public int getNonNegativeInteger(final String param) {
    return get(param, new StringToInteger(),
        new IsNonNegative<Integer>(), "non-negative integer");
  }

  /**
   * Gets a double parameter.
   */
  public double getDouble(final String param) {
    return get(param, new StringToDouble(),
        new AlwaysValid<Double>(), "double");
  }

  /**
   * Gets a double between 0.0 and 1.0, inclusive.
   */
  public double getProbability(final String param) {
    return get(param, new StringToDouble(),
        new IsInRange<Double>(Range.closed(0.0, 1.0)),
        "probability");
  }

  private StringConverter<File> getFileConverter() {
    if (isPresent(DO_OS_CONVERSION_PARAM) &&
        getBoolean(DO_OS_CONVERSION_PARAM)) {
      return new StringToOSFile();
    } else {
      return new StringToFile();
    }
  }

  /**
   * Gets a file, which is required to exist.
   */
  public File getExistingFile(final String param) {
    return get(param, getFileConverter(),
        new And<File>(new FileExists(), new IsFile()),
        "existing file");
  }

  public File getFirstExistingFile(String param) {
    final List<String> fileStrings = getStringList(param);
    for (final String fileName : fileStrings) {
      final File f = new File(fileName.trim());
      if (f.isFile()) {
        return f;
      }
    }

    throw new ParameterConversionException(fullString(param), fileStrings.toString(),
        "No provided path is an existing file");
  }
  /**
   * Gets a file or directory, which is required to exist.
   */
  public File getExistingFileOrDirectory(final String param) {
    return get(param, getFileConverter(), new FileExists(),
        "existing file or directory");
  }

  /**
   * Gets a directory which is guaranteed to exist after the execution of this method.  If the
   * directory does not already exist, it and its parents are created. If this is not possible, an
   * exception is throws.
   */
  public File getAndMakeDirectory(final String param) {
    final File f = get(param, new StringToFile(),
        new AlwaysValid<File>(), "existing or creatable directory");

    if (f.exists()) {
      if (f.isDirectory()) {
        return f.getAbsoluteFile();
      } else {
        throw new ParameterValidationException(fullString(param), f
            .getAbsolutePath().toString(),
            new ValidationException("Not an existing or creatable directory"));
      }
    } else {
      f.getAbsoluteFile().mkdirs();
      return f.getAbsoluteFile();
    }
  }

  /**
   * Gets a directory which already exists.
   */
  public File getExistingDirectory(final String param) {
    return get(param, new StringToFile(),
        new And<File>(new FileExists(), new IsDirectory()),
        "existing directory");
  }

  /**
   * Gets a file or directory parameter without specifying whether it exists. Prefer a more
   * specific parameter accessor when possible.
   */
  public File getFileOrDirectory(final String param) {
    return get(param, new StringToFile(), new AlwaysValid<File>(), "file or directory");
  }

  /**
   * Gets a (possibly empty) list of existing directories. Will throw a {@link
   * com.bbn.bue.common.parameters.exceptions.ParameterValidationException} if any of the supplied
   * paths are not existing directories.
   */
  public ImmutableList<File> getExistingDirectories(String param) {
    final List<String> fileStrings = getStringList(param);
    final ImmutableList.Builder<File> ret = ImmutableList.builder();

    for (final String dirName : fileStrings) {
      final File dir = new File(dirName.trim());
      if (!dir.isDirectory()) {
        throw new ParameterValidationException(fullString(param), dirName,
            "path does not exist or is not a directory");
      }
      ret.add(dir);
    }

    return ret.build();
  }

  /**
   * Gets the first existing directory in a common-separated list. If none exists, throws an {@link
   * com.bbn.bue.common.parameters.exceptions.ParameterValidationException}.
   */
  public File getFirstExistingDirectory(String param) {
    final List<String> directoryStrings = getStringList(param);
    for (final String dirName : directoryStrings) {
      final File dir = new File(dirName.trim());
      if (dir.isDirectory()) {
        return dir;
      }
    }

    throw new ParameterConversionException(fullString(param), directoryStrings.toString(),
        "No provided path is an existing directory");
  }


  public Optional<File> getOptionalExistingDirectory(final String param) {
    if (isPresent(param)) {
      return Optional.of(getExistingDirectory(param));
    }
    return Optional.absent();
  }

  /**
   * Gets a ,-separated set of Strings.
   */
  public Set<String> getStringSet(final String param) {
    return get(param, new StringToStringSet(","),
        new AlwaysValid<Set<String>>(),
        "comma-separated list of strings");
  }

  /**
   * Gets a ,-separated list of Strings
   */
  public List<String> getStringList(final String param) {
    return get(param, new StringToStringList(","),
        new AlwaysValid<List<String>>(),
        "comma-separated list of strings");
  }

  /**
   * Gets a ,-separated list of Strings, if available
   */
  public Optional<List<String>> getOptionalStringList(final String param) {
    if (isPresent(param)) {
      return Optional.of(getStringList(param));
    }
    return Optional.absent();
  }

  /**
   * Gets a ,-separated set of Symbols
   */
  public Set<Symbol> getSymbolSet(final String param) {
    return get(param, new StringToSymbolSet(","),
        new AlwaysValid<Set<Symbol>>(),
        "comma-separated list of strings");
  }

  /**
   * Gets a ,-separated list of Symbols
   */
  public List<Symbol> getSymbolList(final String param) {
    return get(param, new StringToSymbolList(","),
        new AlwaysValid<List<Symbol>>(),
        "comma-separated list of strings");
  }

  public File getCreatableFile(final String param) {
    final String val = getString(param);
    final File ret = new File(val);

    if (ret.exists()) {
      if (ret.isDirectory()) {
        throw new ParameterValidationException(fullString(param), val,
            "Requested a file, but directory exists with that filename");
      }
    } else {
      ret.getAbsoluteFile().getParentFile().mkdirs();
    }

    return ret;
  }

  /**
   * Gets a file, with no requirements about whether it exists or not.
   */
  public File getPossiblyNonexistentFile(final String param) {
    return new File(getString(param));
  }


  public File getCreatableDirectory(final String param) {
    final String val = getString(param);
    final File ret = new File(val);

    if (ret.exists()) {
      if (!ret.isDirectory()) {
        throw new ParameterValidationException(fullString(param), val,
            "Requested a directory, but a file exists with that filename");
      }
    } else {
      ret.getAbsoluteFile().mkdirs();
    }

    return ret;
  }

  public File getEmptyDirectory(final String param) {
    final File dir = getCreatableDirectory(param);
    final int numFilesContained = dir.list().length;

    if (numFilesContained != 0) {
      throw new ParameterValidationException(fullString(param), getString(param),
          String.format("Requested an empty directory, but directory contains %d files.",
              numFilesContained));
    }
    return dir;
  }

  public Parameters getSubParameters(final String param) throws IOException {
    final File paramFile = getExistingFile(param);
    return Parameters.loadSerifStyle(paramFile);
  }

  /**
   * Throws a ParameterException if neither parameter is defined.
   */
  public void assertAtLeastOneDefined(final String param1, final String param2) {
    if (!isPresent(param1) && !isPresent(param2)) {
      throw new ParameterException(
          String.format("At least one of %s and %s must be defined.", param1, param2));
    }
  }

  public Optional<File> getOptionalExistingFile(final String param) {
    if (isPresent(param)) {
      return Optional.of(getExistingFile(param));
    } else {
      return Optional.absent();
    }
  }

  public Optional<File> getOptionalCreatableFile(final String param) {
    if (isPresent(param)) {
      return Optional.of(getCreatableFile(param));
    } else {
      return Optional.absent();
    }
  }

  public File getExistingFileRelativeTo(final File root, final String param) {
    if (!root.exists()) {
      throw new ParameterException(
          String.format("Cannot resolve parameter %s relative to non-existent directory", param),
          new FileNotFoundException(String.format("Not found: %s", root)));
    }
    final String val = getString(param);
    final File ret = new File(root, val);
    if (!ret.exists()) {
      throw new ParameterValidationException(fullString(param), ret.getAbsolutePath(),
          "Requested existing file, but the file does not exist");
    }
    return ret;
  }

  private final Map<String, String> params;
  private final List<String> namespace;

}
