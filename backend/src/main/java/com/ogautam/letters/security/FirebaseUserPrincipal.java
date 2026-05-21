package com.ogautam.letters.security;

import lombok.Getter;

@Getter
public class FirebaseUserPrincipal {
    private final String uid;
    private final String email;
    private final String name;
    private final String picture;

    public FirebaseUserPrincipal(String uid, String email, String name, String picture) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.picture = picture;
    }
}
