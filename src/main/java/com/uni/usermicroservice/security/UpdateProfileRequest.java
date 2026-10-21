package com.uni.usermicroservice.security;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@Size(max = 30) @PhoneFormat String phone) {
}
