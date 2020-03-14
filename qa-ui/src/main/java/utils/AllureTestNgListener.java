/*
 *  Copyright 2019 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package utils;

import com.codeborne.selenide.WebDriverRunner;
import io.qameta.allure.*;
import io.qameta.allure.model.Link;
import io.qameta.allure.model.*;
import io.qameta.allure.selenide.LogType;
import io.qameta.allure.util.AnnotationUtils;
import io.qameta.allure.util.ObjectUtils;
import io.qameta.allure.util.ResultsUtils;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.annotations.Parameters;
import org.testng.internal.ConstructorOrMethod;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.io.ByteArrayInputStream;
import java.lang.annotation.*;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codeborne.selenide.WebDriverRunner.getWebDriver;
import static io.qameta.allure.Allure.addAttachment;
import static io.qameta.allure.util.ResultsUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;

/**
 * Allure TestNG listener.
 */
@SuppressWarnings({
        "PMD.ExcessiveImports", "PMD.TooManyMethods", "PMD.GodClass",
        "ClassFanOutComplexity", "ClassDataAbstractionCoupling", "PMD.ExcessiveClassLength"
})
public class AllureTestNgListener implements
        ISuiteListener,
        ITestListener,
        IInvokedMethodListener2,
        IConfigurationListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureTestNgListener.class);

    private static final String ALLURE_UUID = "ALLURE_UUID";

    /**
     * Store current testng result uuid to attach before/after methods into.
     */
    private final ThreadLocal<Current> currentTestResult = ThreadLocal
            .withInitial(Current::new);

    /**
     * Store current container uuid for fake containers around before/after methods.
     */
    private final ThreadLocal<String> currentTestContainer = ThreadLocal
            .withInitial(() -> UUID.randomUUID().toString());

    /**
     * Store uuid for current executable item to catch steps and attachments.
     */
    private final ThreadLocal<String> currentExecutable = ThreadLocal
            .withInitial(() -> UUID.randomUUID().toString());

    /**
     * Store uuid for class test containers.
     */
    private final Map<ITestClass, String> classContainerUuidStorage = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private static final List<Class<?>> INJECTED_TYPES = Arrays.asList(
            ITestContext.class, ITestResult.class, XmlTest.class, Method.class, Object[].class
    );

    private final AllureLifecycle lifecycle;

    public AllureTestNgListener(final AllureLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public AllureTestNgListener() {
        this.lifecycle = Allure.getLifecycle();
    }

    public AllureLifecycle getLifecycle() {
        return lifecycle;
    }

    @Override
    public void onStart(final ISuite suite) {
        final TestResultContainer result = new TestResultContainer()
                .setUuid(getUniqueUuid(suite))
                .setName(suite.getName())
                .setStart(System.currentTimeMillis());
        getLifecycle().startTestContainer(result);
    }

    @Override
    public void onStart(final ITestContext context) {
        final String parentUuid = getUniqueUuid(context.getSuite());
        final String uuid = getUniqueUuid(context);
        final TestResultContainer container = new TestResultContainer()
                .setUuid(uuid)
                .setName(context.getName())
                .setStart(System.currentTimeMillis());
        getLifecycle().startTestContainer(parentUuid, container);

        Stream.of(context.getAllTestMethods())
                .map(ITestNGMethod::getTestClass)
                .distinct()
                .forEach(this::onBeforeClass);

        context.getExcludedMethods().stream()
                .filter(ITestNGMethod::isTest)
                .filter(method -> !method.getEnabled())
                .forEach(method -> createFakeResult(context, method));
    }

    protected void createFakeResult(final ITestContext context, final ITestNGMethod method) {
        final String uuid = UUID.randomUUID().toString();
        final String parentUuid = UUID.randomUUID().toString();
        startTestCase(context, method, method.getTestClass(), new Object[]{}, parentUuid, uuid);
        stopTestCase(uuid, null, null);
    }

    @Override
    public void onFinish(final ISuite suite) {
        final String uuid = getUniqueUuid(suite);
        getLifecycle().stopTestContainer(uuid);
        getLifecycle().writeTestContainer(uuid);

    }

    @Override
    public void onFinish(final ITestContext context) {
        final String uuid = getUniqueUuid(context);
        getLifecycle().stopTestContainer(uuid);
        getLifecycle().writeTestContainer(uuid);

        Stream.of(context.getAllTestMethods())
                .map(ITestNGMethod::getTestClass)
                .distinct()
                .forEach(this::onAfterClass);
    }

    public void onBeforeClass(final ITestClass testClass) {
        final String uuid = UUID.randomUUID().toString();
        final TestResultContainer container = new TestResultContainer()
                .setUuid(uuid)
                .setName(testClass.getName());
        getLifecycle().startTestContainer(container);
        setClassContainer(testClass, uuid);
    }

    public void onAfterClass(final ITestClass testClass) {
        getClassContainer(testClass).ifPresent(uuid -> {
            getLifecycle().stopTestContainer(uuid);
            getLifecycle().writeTestContainer(uuid);
        });
    }

    @Override
    public void onTestStart(final ITestResult testResult) {
        Current current = currentTestResult.get();
        if (current.isStarted()) {
            current = refreshContext();
        }
        current.test();
        final String uuid = current.getUuid();
        final String parentUuid = getUniqueUuid(testResult.getTestContext());

        startTestCase(testResult, parentUuid, uuid);

        Optional.of(testResult)
                .map(ITestResult::getMethod)
                .map(ITestNGMethod::getTestClass)
                .ifPresent(clazz -> addClassContainerChild(clazz, uuid));
    }

    protected void startTestCase(final ITestResult testResult,
                                 final String parentUuid,
                                 final String uuid) {
        startTestCase(
                testResult.getTestContext(),
                testResult.getMethod(),
                testResult.getTestClass(),
                testResult.getParameters(),
                parentUuid,
                uuid
        );
    }

    @SuppressWarnings({"Indentation", "PMD.ExcessiveMethodLength", "deprecation"})
    protected void startTestCase(final ITestContext context,
                                 final ITestNGMethod method,
                                 final IClass iClass,
                                 final Object[] params,
                                 final String parentUuid,
                                 final String uuid) {
        final ITestClass testClass = method.getTestClass();
        final List<Label> labels = new ArrayList<>();
        labels.addAll(getProvidedLabels());
        labels.addAll(Arrays.asList(
                //Packages grouping
                createPackageLabel(testClass.getName()),
                createTestClassLabel(testClass.getName()),
                createTestMethodLabel(method.getMethodName()),

                //xUnit grouping
                createParentSuiteLabel(safeExtractSuiteName(testClass)),
                createSuiteLabel(safeExtractTestTag(testClass)),
                createSubSuiteLabel(safeExtractTestClassName(testClass)),

                //Timeline grouping
                createHostLabel(),
                createThreadLabel(),

                createFrameworkLabel("testng"),
                createLanguageLabel("java")
        ));
        labels.addAll(getLabels(method, iClass));
        final List<Parameter> parameters = getParameters(context, method, params);
        final TestResult result = new TestResult()
                .setUuid(uuid)
                .setHistoryId(getHistoryId(method, parameters))
                .setName(getMethodName(method))
                .setFullName(getQualifiedName(method))
                .setStatusDetails(new StatusDetails()
                        .setFlaky(isFlaky(method, iClass))
                        .setMuted(isMuted(method, iClass)))
                .setParameters(parameters)
                .setLinks(getLinks(method, iClass))
                .setLabels(labels);
        processDescription(getClass().getClassLoader(), method.getConstructorOrMethod().getMethod(), result);
        getLifecycle().scheduleTestCase(parentUuid, result);
        getLifecycle().startTestCase(uuid);
    }

    @Override
    public void onTestSuccess(final ITestResult testResult) {
        final Current current = currentTestResult.get();
        current.after();

        getScreenshotBytes().ifPresent(bytes -> lifecycle.addAttachment("Screenshot", "image/png", "png", bytes));
        lifecycle.updateStep(step -> step.setStatus(Status.PASSED));
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
        addAttachment("WSConsole log", "text/plain", wsAnalyzeLog());
        addAttachment("Console log", "text/plain", analyzeLog());

        getLifecycle().updateTestCase(current.getUuid(), setStatus(Status.PASSED));
        getLifecycle().stopTestCase(current.getUuid());
        getLifecycle().writeTestCase(current.getUuid());
    }

    @Override
    public void onTestFailure(final ITestResult result) {
        Current current = currentTestResult.get();

        if (current.isAfter()) {
            current = refreshContext();
        }

        //if testng has failed without any setup
        if (!current.isStarted()) {
            createTestResultForTestWithoutSetup(result);
        }

        current.after();
        final String uuid = current.getUuid();

        final Throwable throwable = result.getThrowable();
        final Status status = getStatus(throwable);

        getScreenshotBytes().ifPresent(bytes -> lifecycle.addAttachment("Screenshot", "image/png", "png", bytes));
        lifecycle.updateStep(step -> step.setStatus(Status.PASSED));
        addAttachment("Video report", "text/html", new ByteArrayInputStream(generateHtmlVideoReport(getVideoUrl()).getBytes()), ".html");
        addAttachment("WSConsole log", "text/plain", wsAnalyzeLog());
        addAttachment("Console log", "text/plain", analyzeLog());

        stopTestCase(uuid, throwable, status);
    }

    protected void stopTestCase(final String uuid, final Throwable throwable, final Status status) {
        final StatusDetails details = getStatusDetails(throwable).orElse(null);
        getLifecycle().updateTestCase(uuid, setStatus(status, details));
        getLifecycle().stopTestCase(uuid);
        getLifecycle().writeTestCase(uuid);
    }

    @Override
    public void onTestSkipped(final ITestResult result) {
        Current current = currentTestResult.get();

        //testng is being skipped as dependent on failed testng, closing context for previous testng here
        if (current.isAfter()) {
            current = refreshContext();
        }

        //if testng was skipped without any setup
        if (!current.isStarted()) {
            createTestResultForTestWithoutSetup(result);
        }
        current.after();
        stopTestCase(current.getUuid(), result.getThrowable(), Status.SKIPPED);
    }

    private void createTestResultForTestWithoutSetup(final ITestResult result) {
        onTestStart(result);
        currentTestResult.remove();
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(final ITestResult result) {
        //do nothing
    }

    @Override
    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult) {
        //do nothing
    }

    @Override
    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult,
                                 final ITestContext context) {
        final ITestNGMethod testMethod = method.getTestMethod();
        if (isSupportedConfigurationFixture(testMethod)) {
            ifSuiteFixtureStarted(context.getSuite(), testMethod);
            ifTestFixtureStarted(context, testMethod);
            ifClassFixtureStarted(testMethod);
            ifMethodFixtureStarted(testMethod);
        }
    }

    private void ifSuiteFixtureStarted(final ISuite suite, final ITestNGMethod testMethod) {
        if (testMethod.isBeforeSuiteConfiguration()) {
            startBefore(getUniqueUuid(suite), testMethod);
        }
        if (testMethod.isAfterSuiteConfiguration()) {
            startAfter(getUniqueUuid(suite), testMethod);
        }
    }

    private void ifClassFixtureStarted(final ITestNGMethod testMethod) {
        if (testMethod.isBeforeClassConfiguration()) {
            getClassContainer(testMethod.getTestClass())
                    .ifPresent(parentUuid -> startBefore(parentUuid, testMethod));
        }
        if (testMethod.isAfterClassConfiguration()) {
            getClassContainer(testMethod.getTestClass())
                    .ifPresent(parentUuid -> startAfter(parentUuid, testMethod));
        }
    }

    private void ifTestFixtureStarted(final ITestContext context, final ITestNGMethod testMethod) {
        if (testMethod.isBeforeTestConfiguration()) {
            startBefore(getUniqueUuid(context), testMethod);
        }
        if (testMethod.isAfterTestConfiguration()) {
            startAfter(getUniqueUuid(context), testMethod);
        }
    }

    private void startBefore(final String parentUuid, final ITestNGMethod method) {
        final String uuid = currentExecutable.get();
        getLifecycle().startPrepareFixture(parentUuid, uuid, getFixtureResult(method));
    }

    private void startAfter(final String parentUuid, final ITestNGMethod method) {
        final String uuid = currentExecutable.get();
        getLifecycle().startTearDownFixture(parentUuid, uuid, getFixtureResult(method));
    }

    private void ifMethodFixtureStarted(final ITestNGMethod testMethod) {
        currentTestContainer.remove();
        Current current = currentTestResult.get();
        final FixtureResult fixture = getFixtureResult(testMethod);
        final String uuid = currentExecutable.get();
        if (testMethod.isBeforeMethodConfiguration()) {
            if (current.isStarted()) {
                currentTestResult.remove();
                current = currentTestResult.get();
            }
            getLifecycle().startPrepareFixture(createFakeContainer(testMethod, current), uuid, fixture);
        }

        if (testMethod.isAfterMethodConfiguration()) {
            getLifecycle().startTearDownFixture(createFakeContainer(testMethod, current), uuid, fixture);
        }
    }

    private String createFakeContainer(final ITestNGMethod method, final Current current) {
        final String parentUuid = currentTestContainer.get();
        final TestResultContainer container = new TestResultContainer()
                .setUuid(parentUuid)
                .setName(getQualifiedName(method))
                .setStart(System.currentTimeMillis())
                .setDescription(method.getDescription())
                .setChildren(Collections.singletonList(current.getUuid()));
        getLifecycle().startTestContainer(container);
        return parentUuid;
    }

    private String getQualifiedName(final ITestNGMethod method) {
        return method.getRealClass().getName() + "." + method.getMethodName();
    }

    @SuppressWarnings("deprecation")
    private FixtureResult getFixtureResult(final ITestNGMethod method) {
        final FixtureResult fixtureResult = new FixtureResult()
                .withName(getMethodName(method))
                .withStart(System.currentTimeMillis())
                .withDescription(method.getDescription())
                .withStage(Stage.RUNNING);
        processDescription(getClass().getClassLoader(), method.getConstructorOrMethod().getMethod(), fixtureResult);
        return fixtureResult;
    }

    @Override
    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult) {
        //do nothing
    }

    @Override
    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult,
                                final ITestContext context) {
        final ITestNGMethod testMethod = method.getTestMethod();
        if (isSupportedConfigurationFixture(testMethod)) {
            final String executableUuid = currentExecutable.get();
            currentExecutable.remove();
            if (testResult.isSuccess()) {
                getLifecycle().updateFixture(executableUuid, result -> result.setStatus(Status.PASSED));
            } else {
                getLifecycle().updateFixture(executableUuid, result -> result
                        .setStatus(getStatus(testResult.getThrowable()))
                        .setStatusDetails(getStatusDetails(testResult.getThrowable()).orElse(null)));
            }
            getLifecycle().stopFixture(executableUuid);

            if (testMethod.isBeforeMethodConfiguration() || testMethod.isAfterMethodConfiguration()) {
                final String containerUuid = currentTestContainer.get();
                validateContainerExists(getQualifiedName(testMethod), containerUuid);
                currentTestContainer.remove();
                getLifecycle().stopTestContainer(containerUuid);
                getLifecycle().writeTestContainer(containerUuid);
            }
        }
    }

    @Override
    public void onConfigurationSuccess(final ITestResult itr) {
        //do nothing
    }

    @Override
    public void onConfigurationFailure(final ITestResult itr) {
        final String uuid = UUID.randomUUID().toString();
        final String parentUuid = UUID.randomUUID().toString();

        startTestCase(itr, parentUuid, uuid);
        stopTestCase(uuid, itr.getThrowable(), getStatus(itr.getThrowable()));
        //do nothing
    }

    @Override
    public void onConfigurationSkip(final ITestResult itr) {
        //do nothing
    }

    protected String getHistoryId(final ITestNGMethod method, final List<Parameter> parameters) {
        final MessageDigest digest = getMd5Digest();
        final String testClassName = method.getTestClass().getName();
        final String methodName = method.getMethodName();
        digest.update(testClassName.getBytes(UTF_8));
        digest.update(methodName.getBytes(UTF_8));
        parameters.stream()
                .sorted(comparing(Parameter::getName).thenComparing(Parameter::getValue))
                .forEachOrdered(parameter -> {
                    digest.update(parameter.getName().getBytes(UTF_8));
                    digest.update(parameter.getValue().getBytes(UTF_8));
                });
        final byte[] bytes = digest.digest();
        return bytesToHex(bytes);
    }

    protected Status getStatus(final Throwable throwable) {
        return ResultsUtils.getStatus(throwable).orElse(Status.BROKEN);
    }

    @SuppressWarnings("BooleanExpressionComplexity")
    private boolean isSupportedConfigurationFixture(final ITestNGMethod testMethod) {
        return testMethod.isBeforeMethodConfiguration() || testMethod.isAfterMethodConfiguration()
                || testMethod.isBeforeTestConfiguration() || testMethod.isAfterTestConfiguration()
                || testMethod.isBeforeClassConfiguration() || testMethod.isAfterClassConfiguration()
                || testMethod.isBeforeSuiteConfiguration() || testMethod.isAfterSuiteConfiguration();
    }

    private void validateContainerExists(final String fixtureName, final String containerUuid) {
        if (Objects.isNull(containerUuid)) {
            throw new IllegalStateException(
                    "Could not find container for after method fixture " + fixtureName
            );
        }
    }

    private List<Label> getLabels(final ITestNGMethod method, final IClass iClass) {
        final List<Label> labels = new ArrayList<>();
        getMethod(method)
                .map(AnnotationUtils::getLabels)
                .ifPresent(labels::addAll);
        getClass(iClass)
                .map(AnnotationUtils::getLabels)
                .ifPresent(labels::addAll);

        getMethod(method)
                .map(this::getSeverity)
                .filter(Optional::isPresent)
                .orElse(getClass(iClass).flatMap(this::getSeverity))
                .map(ResultsUtils::createSeverityLabel)
                .ifPresent(labels::add);
        return labels;
    }

    private Optional<SeverityLevel> getSeverity(final AnnotatedElement annotatedElement) {
        return Stream.of(annotatedElement.getAnnotationsByType(Severity.class))
                .map(Severity::value)
                .findAny();
    }

    private List<Link> getLinks(final ITestNGMethod method, final IClass iClass) {
        final List<Link> links = new ArrayList<>();
        getMethod(method)
                .map(AnnotationUtils::getLinks)
                .ifPresent(links::addAll);
        getClass(iClass)
                .map(AnnotationUtils::getLinks)
                .ifPresent(links::addAll);
        return links;
    }

    private boolean isFlaky(final ITestNGMethod method, final IClass iClass) {
        final boolean flakyMethod = getMethod(method)
                .map(m -> m.isAnnotationPresent(Flaky.class))
                .orElse(false);
        final boolean flakyClass = getClass(iClass)
                .map(clazz -> clazz.isAnnotationPresent(Flaky.class))
                .orElse(false);
        return flakyMethod || flakyClass;
    }

    private boolean isMuted(final ITestNGMethod method, final IClass iClass) {
        final boolean mutedMethod = getMethod(method)
                .map(m -> m.isAnnotationPresent(Muted.class))
                .orElse(false);
        final boolean mutedClass = getClass(iClass)
                .map(clazz -> clazz.isAnnotationPresent(Muted.class))
                .orElse(false);
        return mutedMethod || mutedClass;
    }

    private Optional<Method> getMethod(final ITestNGMethod method) {
        return Optional.ofNullable(method)
                .map(ITestNGMethod::getConstructorOrMethod)
                .map(ConstructorOrMethod::getMethod);
    }

    private Optional<Class<?>> getClass(final IClass iClass) {
        return Optional.ofNullable(iClass)
                .map(IClass::getRealClass);
    }

    /**
     * Returns the unique id for given results item.
     */
    private String getUniqueUuid(final IAttributes suite) {
        if (Objects.isNull(suite.getAttribute(ALLURE_UUID))) {
            suite.setAttribute(ALLURE_UUID, UUID.randomUUID().toString());
        }
        return Objects.toString(suite.getAttribute(ALLURE_UUID));
    }

    private static String safeExtractSuiteName(final ITestClass testClass) {
        final Optional<XmlTest> xmlTest = Optional.ofNullable(testClass.getXmlTest());
        return xmlTest.map(XmlTest::getSuite).map(XmlSuite::getName).orElse("Undefined suite");
    }

    private static String safeExtractTestTag(final ITestClass testClass) {
        final Optional<XmlTest> xmlTest = Optional.ofNullable(testClass.getXmlTest());
        return xmlTest.map(XmlTest::getName).orElse("Undefined testng tag");
    }

    private static String safeExtractTestClassName(final ITestClass testClass) {
        return firstNonEmpty(testClass.getTestName(), testClass.getName()).orElse("Undefined class name");
    }

    private List<Parameter> getParameters(final ITestContext context,
                                          final ITestNGMethod method,
                                          final Object... parameters) {
        final Map<String, String> result = new HashMap<>(
                context.getCurrentXmlTest().getAllParameters()
        );
        final Object instance = method.getInstance();
        if (nonNull(instance)) {
            Stream.of(instance.getClass().getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(TestInstanceParameter.class))
                    .forEach(field -> {
                        final String name = Optional.ofNullable(field.getAnnotation(TestInstanceParameter.class))
                                .map(TestInstanceParameter::value)
                                .filter(s -> !s.isEmpty())
                                .orElseGet(field::getName);
                        try {
                            field.setAccessible(true);
                            final String value = ObjectUtils.toString(field.get(instance));
                            result.put(name, value);
                        } catch (IllegalAccessException e) {
                            LOGGER.debug("Could not access field value");
                        }
                    });
        }

        getMethod(method).ifPresent(m -> {
            final Class<?>[] parameterTypes = m.getParameterTypes();

            if (parameterTypes.length != parameters.length) {
                return;
            }

            final String[] providedNames = Optional.ofNullable(m.getAnnotation(Parameters.class))
                    .map(Parameters::value)
                    .orElse(new String[]{});

            final String[] reflectionNames = Stream.of(m.getParameters())
                    .map(java.lang.reflect.Parameter::getName)
                    .toArray(String[]::new);

            int skippedCount = 0;
            for (int i = 0; i < parameterTypes.length; i++) {
                final Class<?> parameterType = parameterTypes[i];
                if (INJECTED_TYPES.contains(parameterType)) {
                    skippedCount++;
                    continue;
                }

                final int indexFromAnnotation = i - skippedCount;
                if (indexFromAnnotation < providedNames.length) {
                    result.put(providedNames[indexFromAnnotation], ObjectUtils.toString(parameters[i]));
                    continue;
                }

                if (i < reflectionNames.length) {
                    result.put(reflectionNames[i], ObjectUtils.toString(parameters[i]));
                }
            }

        });

        return result.entrySet().stream()
                .map(entry -> createParameter(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private String getMethodName(final ITestNGMethod method) {
        return firstNonEmpty(
                method.getDescription(),
                method.getMethodName(),
                getQualifiedName(method)).orElse("Unknown");
    }

    @SuppressWarnings("SameParameterValue")
    private Consumer<TestResult> setStatus(final Status status) {
        return result -> result.setStatus(status);
    }

    private Consumer<TestResult> setStatus(final Status status, final StatusDetails details) {
        return result -> {
            result.setStatus(status);
            if (nonNull(details)) {
                result.getStatusDetails().setTrace(details.getTrace());
                result.getStatusDetails().setMessage(details.getMessage());
            }
        };
    }

    private void addClassContainerChild(final ITestClass clazz, final String childUuid) {
        lock.writeLock().lock();
        try {
            final String parentUuid = classContainerUuidStorage.get(clazz);
            if (nonNull(parentUuid)) {
                getLifecycle().updateTestContainer(
                        parentUuid,
                        container -> container.getChildren().add(childUuid)
                );
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Optional<String> getClassContainer(final ITestClass clazz) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(classContainerUuidStorage.get(clazz));
        } finally {
            lock.readLock().unlock();
        }
    }

    private void setClassContainer(final ITestClass clazz, final String uuid) {
        lock.writeLock().lock();
        try {
            classContainerUuidStorage.put(clazz, uuid);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Current refreshContext() {
        currentTestResult.remove();
        return currentTestResult.get();
    }

    /**
     * Describes current test result.
     */
    private static class Current {
        private final String uuid;
        private CurrentStage currentStage;

        Current() {
            this.uuid = UUID.randomUUID().toString();
            this.currentStage = CurrentStage.BEFORE;
        }

        public void test() {
            this.currentStage = CurrentStage.TEST;
        }

        public void after() {
            this.currentStage = CurrentStage.AFTER;
        }

        public boolean isStarted() {
            return this.currentStage != CurrentStage.BEFORE;
        }

        public boolean isAfter() {
            return this.currentStage == CurrentStage.AFTER;
        }

        public String getUuid() {
            return uuid;
        }
    }

    /**
     * The stage of current result context.
     */
    private enum CurrentStage {
        BEFORE,
        TEST,
        AFTER
    }

    /**
     * You can use this annotation to add test instance parameters to your TestNG tests.
     * Has been copy from allure-testng library.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface TestInstanceParameter {

        /**
         * The name of parameter.
         */
        String value() default "";
    }



    private static Optional<byte[]> getScreenshotBytes() {
        try {
            return WebDriverRunner.hasWebDriverStarted()
                    ? Optional.of(((TakesScreenshot) WebDriverRunner.getWebDriver()).getScreenshotAs(OutputType.BYTES))
                    : Optional.empty();
        } catch (WebDriverException e) {
            LOGGER.warn("Could not get screen shot", e);
            return Optional.empty();
        }
    }

    public static URL getVideoUrl() {
        SessionId sessionId = null;
        try {
            sessionId = ((RemoteWebDriver) WebDriverRunner.getWebDriver()).getSessionId();
        } catch (ClassCastException ex) {
            LOGGER.warn("The driver cannot be casted to a RemoteWebDriver. Probably it has been run one type of a WebDriver, for example ChromeDriver, FirefoxDriver and so on");
            LOGGER.warn("Video can be recorded only by the RemoveWedDriver instance which has been to run on the Selenoid infrastructure");
        } catch (IllegalStateException ex) {
            LOGGER.warn(" No webdriver is bound to current thread: 1. You need to call open(url) first.");
            LOGGER.warn("Video can be recorded only by the RemoveWedDriver instance which has been to run on the Selenoid infrastructure");
        }

        URL videoUrl = null;
        try {
            videoUrl = new URL("http://localhost:4444/video/" + sessionId + ".mp4");
        } catch (MalformedURLException e) {
            LOGGER.error("Can't get the video url for session: " + sessionId + " " + e.getMessage());
        }
        return videoUrl;
    }

    public static String generateHtmlVideoReport(URL videoUrl) {
        Document doc = Jsoup.parseBodyFragment("<video><source></video>");
        doc.getElementsByTag("video").get(0)
                .attr("width", "1366")
                .attr("height", "768")
                .attr("controls", "controls");

        doc.getElementsByTag("source").get(0)
                .attr("src", videoUrl.toString())
                .attr("type", "video/mp4");
        return doc.toString();
    }

    public static String analyzeLog() {
        StringBuilder builder = new StringBuilder();
        LogEntries logEntries = WebDriverRunner.getWebDriver().manage().logs().get(String.valueOf(LogType.BROWSER));
        for (LogEntry entry : logEntries) {
            builder.append(new Date(entry.getTimestamp()))
                    .append(entry.getLevel())
                    .append(entry.getMessage())
                    .append("\n");
        }
        return builder.toString();
    }

    public static String wsAnalyzeLog() {
        StringBuilder builder = new StringBuilder();
        LogEntries logEntries = getWebDriver()
                .manage()
                .logs()
                .get(org.openqa.selenium.logging.LogType.PERFORMANCE);
        logEntries.forEach(entry -> {
            JSONObject messageJSON = new JSONObject(entry.getMessage());
            String method = messageJSON.getJSONObject("message").getString("method");
            if (method.equalsIgnoreCase("Network.webSocketFrameSent")) {
                builder
                        .append("Message Sent: ")
                        .append("\n")
                        .append(messageJSON.getJSONObject("message").getJSONObject("params").getJSONObject("response").getString("payloadData"))
                        .append("\n");
            } else if (method.equalsIgnoreCase("Network.webSocketFrameReceived")) {
                builder
                        .append("Message Received: ")
                        .append("\n")
                        .append(messageJSON.getJSONObject("message").getJSONObject("params").getJSONObject("response").getString("payloadData"))
                        .append("\n");
            }
        });
        return builder.toString();
    }
}
