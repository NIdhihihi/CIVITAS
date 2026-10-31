package com.civitas.platform.modules.voting;

import com.civitas.platform.common.CryptoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class BallotCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final PedersenZkService zk;
    private final ObjectMapper mapper;
    private final String masterSecret;

    public BallotCryptoService(PedersenZkService zk, ObjectMapper mapper,
                               @Value("${civitas.security.master-secret}") String masterSecret) {
        this.zk = zk;
        this.mapper = mapper;
        this.masterSecret = masterSecret;
    }

    public SealedBallot seal(String electionId, String postId, String nullifier, int optionIndex,
                             String type, String choiceId) {
        try {
            String context = context(electionId, postId, nullifier);
            PedersenZkService.ZkProof proof = zk.prove(optionIndex, context);
            Payload payload = new Payload(type, choiceId, optionIndex, proof.randomizer());
            byte[] plaintext = mapper.writeValueAsBytes(payload);
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(electionId), new GCMParameterSpec(128, iv));
            cipher.updateAAD((electionId + ":" + postId).getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new SealedBallot(ENCODER.encodeToString(ciphertext), ENCODER.encodeToString(iv),
                    proof.commitment(), proof.announcement(), proof.s1(), proof.s2());
        } catch (Exception e) {
            throw new IllegalStateException("Could not cryptographically seal the ballot.", e);
        }
    }

    public OpenedBallot open(Vote vote) {
        try {
            String electionId = vote.getElection().getId().toString();
            String postId = vote.getPost().getId().toString();
            String context = context(electionId, postId, vote.getNullifier());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(electionId),
                    new GCMParameterSpec(128, DECODER.decode(vote.getIv())));
            cipher.updateAAD((electionId + ":" + postId).getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(DECODER.decode(vote.getEncryptedChoice()));
            Payload payload = mapper.readValue(plaintext, Payload.class);
            boolean proofValid = zk.verify(payload.index(), context, vote.getCommitment(), vote.getProofA(),
                    vote.getProofS1(), vote.getProofS2(), payload.randomizer());
            return new OpenedBallot(payload.type(), payload.id(), payload.index(), proofValid);
        } catch (Exception e) {
            throw new IllegalStateException("Could not open ballot " + vote.getId() + ".", e);
        }
    }

    private SecretKeySpec key(String electionId) {
        byte[] key = CryptoUtils.hmacSha256Bytes(masterSecret.getBytes(StandardCharsets.UTF_8),
                ("ballot-key:" + electionId).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private String context(String electionId, String postId, String nullifier) {
        return "CIVITAS-BALLOT-v1|" + electionId + "|" + postId + "|" + nullifier;
    }

    public record SealedBallot(String ciphertext, String iv, String commitment, String announcement,
                               String s1, String s2) {
    }

    public record OpenedBallot(String type, String id, int index, boolean proofValid) {
    }

    private record Payload(String type, String id, int index, String randomizer) {
    }
}
