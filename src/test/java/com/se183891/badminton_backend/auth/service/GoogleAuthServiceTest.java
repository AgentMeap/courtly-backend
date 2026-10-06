package com.se183891.badminton_backend.auth.service;

import com.se183891.badminton_backend.common.exception.ApiException;
import com.se183891.badminton_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GoogleAuthServiceTest {

    @Test
    void returns503WhenNoVerifierIsEnabled() {
        ObjectProvider<GoogleIdentityVerifier> none =
                new StaticListableBeanFactory().getBeanProvider(GoogleIdentityVerifier.class);
        UserRepository userRepository = mock(UserRepository.class);
        GoogleAuthService service = new GoogleAuthService(none, userRepository, mock(AuthService.class));

        assertThatThrownBy(() -> service.loginWithGoogle("mock:demo@example.com:Demo"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(userRepository);
    }
}
