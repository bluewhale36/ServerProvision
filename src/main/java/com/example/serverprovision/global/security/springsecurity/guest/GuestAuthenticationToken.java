package com.example.serverprovision.global.security.springsecurity.guest;

import com.example.serverprovision.global.security.springsecurity.domain.MachineAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Collection;

/** 게스트 AuthenticationToken — authentication 전에는 credential 을, 후에는 {@link GuestPrincipal} 과 {@link MachineAuthority#GUEST} 를 든다. */
public class GuestAuthenticationToken extends AbstractAuthenticationToken {

	private final Object principal;
	private final GuestCredential credential;

	private GuestAuthenticationToken(
			Object principal, GuestCredential credential, Collection<? extends GrantedAuthority> authorities, boolean authenticated
	) {
		super(authorities);
		this.principal = principal;
		this.credential = credential;
		super.setAuthenticated(authenticated);
	}

	public static GuestAuthenticationToken unauthenticated(GuestCredential credential) {
		return new GuestAuthenticationToken(null, credential, null, false);
	}

	public static GuestAuthenticationToken authenticated(GuestPrincipal principal) {
		return new GuestAuthenticationToken(
				principal, null, AuthorityUtils.createAuthorityList(MachineAuthority.GUEST.getAuthority()), true
		);
	}

	@Override
	public Object getCredentials() {
		return credential;
	}

	@Override
	public Object getPrincipal() {
		return principal;
	}

	public GuestCredential credential() {
		return credential;
	}

	@Override
	public String getName() {
		return principal instanceof GuestPrincipal p ? p.guestServerId().toString() : "guest";
	}
}
