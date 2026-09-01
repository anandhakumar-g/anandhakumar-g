package com.singlepoint.flat;

import com.singlepoint.common.error.AppException;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.user.domain.MembershipRelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class InviteCodeService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    private final InviteCodeRepository repository;
    private final FlatRepository flatRepository;

    public InviteCodeService(InviteCodeRepository repository, FlatRepository flatRepository) {
        this.repository = repository;
        this.flatRepository = flatRepository;
    }

    @Transactional
    public InviteCode create(UUID tenantId, UUID createdByUserId, UUID flatId, MembershipRelation relation,
                             Integer validDays, Integer maxUses) {
        if (flatId != null) {
            flatRepository.findByIdAndTenantId(flatId, tenantId)
                    .orElseThrow(() -> AppException.notFound("Flat"));
        }
        InviteCode c = new InviteCode();
        c.setTenantId(tenantId);
        c.setFlatId(flatId);
        c.setCreatedByUserId(createdByUserId);
        c.setRelation(relation != null ? relation : MembershipRelation.OCCUPANT);
        c.setCode(uniqueCode());
        c.setMaxUses(maxUses != null && maxUses > 0 ? maxUses : 1);
        c.setExpiresAt(Instant.now().plus(validDays != null && validDays > 0 ? validDays : 14, ChronoUnit.DAYS));
        return repository.save(c);
    }

    @Transactional
    public InviteCode revoke(UUID tenantId, UUID codeId) {
        InviteCode c = repository.findById(codeId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("Invite code"));
        c.setStatus(InviteCode.Status.REVOKED);
        return repository.save(c);
    }

    @Transactional(readOnly = true)
    public List<InviteCode> listForTenant(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            String code = sb.toString();
            if (repository.findByCode(code).isEmpty()) return code;
        }
        throw new AppException(com.singlepoint.common.error.ErrorCode.INTERNAL, "Could not allocate an invite code");
    }
}
