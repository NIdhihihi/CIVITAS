package com.civitas.platform.modules.voting;

import com.civitas.platform.common.CryptoUtils;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Demonstration-grade Pedersen commitment with a Schnorr proof of knowledge.
 *
 * A ballot is committed as C = g^m * h^r (mod p). The proof convinces the verifier
 * that the server knows the committed option index and randomizer without putting
 * the index in the clear. The encrypted ballot payload carries r so the tally step
 * can re-open and verify the commitment. This is not a substitute for an
 * end-to-end verifiable election protocol or an independent cryptographic audit.
 */
@Service
public class PedersenZkService {
    private static final BigInteger P = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
            "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
            "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
            "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
            "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
            "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);
    private static final BigInteger Q = P.subtract(BigInteger.ONE).shiftRight(1);
    private static final BigInteger G = BigInteger.TWO;
    // h is a fixed group element. This deterministic demo constant must be replaced by a
    // verifiably random element whose discrete log is not known in a production ceremony.
    private static final BigInteger H = G.modPow(new BigInteger(
            CryptoUtils.sha256("CIVITAS-PEDERSEN-H-v1"), 16).mod(Q), P);
    private static final SecureRandom RANDOM = new SecureRandom();

    public ZkProof prove(int optionIndex, String context) {
        BigInteger m = BigInteger.valueOf(optionIndex);
        BigInteger r = random();
        BigInteger commitment = G.modPow(m, P).multiply(H.modPow(r, P)).mod(P);
        BigInteger u1 = random();
        BigInteger u2 = random();
        BigInteger announcement = G.modPow(u1, P).multiply(H.modPow(u2, P)).mod(P);
        BigInteger challenge = challenge(context, commitment, announcement);
        BigInteger s1 = u1.add(challenge.multiply(m)).mod(Q);
        BigInteger s2 = u2.add(challenge.multiply(r)).mod(Q);
        return new ZkProof(commitment.toString(16), announcement.toString(16), s1.toString(16), s2.toString(16),
                r.toString(16));
    }

    public boolean verify(int optionIndex, String context, String commitmentHex, String announcementHex,
                          String s1Hex, String s2Hex, String randomizerHex) {
        try {
            BigInteger commitment = new BigInteger(commitmentHex, 16);
            BigInteger announcement = new BigInteger(announcementHex, 16);
            BigInteger s1 = new BigInteger(s1Hex, 16);
            BigInteger s2 = new BigInteger(s2Hex, 16);
            BigInteger r = new BigInteger(randomizerHex, 16);
            if (commitment.signum() <= 0 || announcement.signum() <= 0 || s1.signum() < 0 || s2.signum() < 0) {
                return false;
            }
            BigInteger expected = G.modPow(BigInteger.valueOf(optionIndex), P).multiply(H.modPow(r, P)).mod(P);
            if (!expected.equals(commitment)) {
                return false;
            }
            BigInteger challenge = challenge(context, commitment, announcement);
            BigInteger left = G.modPow(s1, P).multiply(H.modPow(s2, P)).mod(P);
            BigInteger right = announcement.multiply(commitment.modPow(challenge, P)).mod(P);
            return left.equals(right);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private BigInteger challenge(String context, BigInteger commitment, BigInteger announcement) {
        String input = context + "|" + commitment.toString(16) + "|" + announcement.toString(16);
        return new BigInteger(CryptoUtils.sha256(input), 16).mod(Q);
    }

    private BigInteger random() {
        return new BigInteger(256, RANDOM).mod(Q);
    }

    public record ZkProof(String commitment, String announcement, String s1, String s2, String randomizer) {
    }
}
