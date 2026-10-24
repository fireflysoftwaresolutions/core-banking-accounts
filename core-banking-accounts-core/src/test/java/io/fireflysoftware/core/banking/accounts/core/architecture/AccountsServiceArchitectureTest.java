package io.fireflysoftware.core.banking.accounts.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.beans.Introspector;
import java.util.List;
import java.util.Set;

class AccountsServiceArchitectureTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionAdvice {}

    private static final String SERVICES = "io.fireflysoftware.core.banking.accounts.core.services";
    private static final Set<String> BEANS =
            Set.of(
                    "accountBalanceServiceImpl",
                    "accountServiceImpl",
                    "assetPriceServiceImpl",
                    "accountFundsService",
                    "servicingMandateService",
                    "accountNotificationServiceImpl",
                    "accountParameterServiceImpl",
                    "customerPositionService",
                    "depositProductService",
                    "accountProviderServiceImpl",
                    "accountRestrictionServiceImpl",
                    "accountSpaceServiceImpl",
                    "accountSpaceTransactionServiceImpl",
                    "accountStatusHistoryServiceImpl");

    private List<Class<?>> services() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        var classes = new java.util.ArrayList<Class<?>>();
        for (var definition : scanner.findCandidateComponents(SERVICES)) {
            classes.add(Class.forName(definition.getBeanClassName()));
        }
        return classes;
    }

    @Test
    void businessServicesExposeTheirFunctionalParentInterfaceFromImplPackages() throws Exception {
        var services = services();
        assertThat(services).hasSize(14);
        for (var implementation : services) {
            String packageName = implementation.getPackageName();
            assertThat(packageName).as(implementation.getName()).endsWith(".impl");
            assertThat(packageName).doesNotMatch(".*\\.v[0-9]+(?:\\..*)?");
            String parent = packageName.substring(0, packageName.length() - ".impl".length());
            assertThat(implementation.getInterfaces())
                    .anySatisfy(
                            contract -> {
                                assertThat(contract.isInterface()).isTrue();
                                assertThat(contract.getPackageName()).isEqualTo(parent);
                                assertThat(implementation.getSimpleName())
                                        .isEqualTo(contract.getSimpleName() + "Impl");
                            });
        }
    }

    @Test
    void actualSpringContextPreservesAllFourteenBusinessBeanIdentities() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(TransactionAdvice.class);
            context.registerBean(DatabaseClient.class, () -> mock(DatabaseClient.class));
            context.registerBean(
                    ReactiveTransactionManager.class, () -> mock(ReactiveTransactionManager.class));
            var resources =
                    new PathMatchingResourcePatternResolver()
                            .getResources(
                                    "classpath*:io/fireflysoftware/core/banking/accounts/models/repositories/**/*.class");
            var reader =
                    new org.springframework.core.type.classreading.CachingMetadataReaderFactory();
            for (var resource : resources) {
                Class<?> type =
                        Class.forName(
                                reader.getMetadataReader(resource)
                                        .getClassMetadata()
                                        .getClassName());
                if (type.isInterface() && Repository.class.isAssignableFrom(type)) {
                    registerRepository(context, type);
                }
            }
            context.scan(SERVICES, "io.fireflysoftware.core.banking.accounts.core.mappers");
            context.refresh();
            assertThat(context.getBeansWithAnnotation(Service.class).keySet())
                    .containsExactlyInAnyOrderElementsOf(BEANS);
            for (String name : BEANS) {
                assertThat(AopUtils.isAopProxy(context.getBean(name)))
                        .as(name + " transaction advice")
                        .isEqualTo(name.endsWith("Impl") || name.equals("accountFundsService"));
            }
            Class<?> funds = AopUtils.getTargetClass(context.getBean("accountFundsService"));
            var attributes = new AnnotationTransactionAttributeSource();
            for (var method : funds.getDeclaredMethods()) {
                if (Set.of("begin", "link", "verify", "control").contains(method.getName())) {
                    assertThat(attributes.getTransactionAttribute(method, funds) != null)
                            .as("funds." + method.getName() + " transaction boundary")
                            .isEqualTo(!method.getName().equals("control"));
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerRepository(AnnotationConfigApplicationContext context, Class type) {
        context.registerBean(
                Introspector.decapitalize(type.getSimpleName()), type, () -> mock(type));
    }
}
