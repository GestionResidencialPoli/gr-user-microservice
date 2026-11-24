package com.uni.usermicroservice.communications;

import com.uni.usermicroservice.security.PasswordChangedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PasswordChangedNotifierTest {

    @Test
    void notifiesTheUserWhosePasswordChanged() {
        CommunicationsClient client = mock(CommunicationsClient.class);

        new PasswordChangedNotifier(client).onPasswordChanged(new PasswordChangedEvent(7L));

        verify(client).notifyUser("usuario.contrasena-actualizada", 7L);
    }
}
