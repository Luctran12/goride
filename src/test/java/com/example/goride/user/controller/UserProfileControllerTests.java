package com.example.goride.user.controller;

import com.example.goride.common.security.CurrentUser;
import com.example.goride.storage.domain.StoredFile;
import com.example.goride.storage.domain.UploadPurpose;
import com.example.goride.storage.service.FileStorageService;
import com.example.goride.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileControllerTests {
    @Test
    void uploadsAvatarAndUpdatesCurrentUserProfile() {
        UserService userService = mock(UserService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        Authentication authentication = mock(Authentication.class);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        StoredFile storedFile = new StoredFile(
                "/uploads/avatars/10/avatar.png",
                "avatars/10/avatar.png",
                "image/png",
                3
        );
        when(currentUser.requireUserId(authentication)).thenReturn(10L);
        when(fileStorageService.store(file, UploadPurpose.USER_AVATAR, 10L)).thenReturn(storedFile);
        UserProfileController controller = new UserProfileController(userService, fileStorageService, currentUser);

        var response = controller.uploadMyAvatar(authentication, file);

        assertThat(response.data().avatarUrl()).isEqualTo(storedFile.url());
        verify(fileStorageService).store(file, UploadPurpose.USER_AVATAR, 10L);
        verify(userService).updateMyAvatar(10L, storedFile.url());
    }
}