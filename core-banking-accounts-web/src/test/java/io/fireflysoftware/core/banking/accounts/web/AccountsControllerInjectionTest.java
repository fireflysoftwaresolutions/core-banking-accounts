package io.fireflysoftware.core.banking.accounts.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

class AccountsControllerInjectionTest {
    @Test
    void controllersDependOnBusinessInterfaces() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int serviceFields = 0;
        for (var definition :
                scanner.findCandidateComponents(
                        "io.fireflysoftware.core.banking.accounts.web.controllers")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            for (var field : controller.getDeclaredFields()) {
                if (field.getType()
                        .getPackageName()
                        .startsWith("io.fireflysoftware.core.banking.accounts.core.services")) {
                    serviceFields++;
                    assertThat(field.getType().isInterface())
                            .as(controller.getName() + "." + field.getName())
                            .isTrue();
                }
            }
        }
        assertThat(serviceFields).isGreaterThanOrEqualTo(14);
    }
}
