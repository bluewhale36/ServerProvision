package com.example.serverprovision.global.security.springsecurity.guest.web;

import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 인자에 게스트 체인이 세운 {@link GuestPrincipal} 을 넣는다({@link CurrentGuestArgumentResolver}). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentGuest {
}
