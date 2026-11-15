package dev.no.name.blooddesigner.net.e2e;

import dev.no.name.blooddesigner.net.crypto.Bytes;
import dev.no.name.blooddesigner.net.crypto.DhKey;
import dev.no.name.blooddesigner.net.crypto.KemKey;

import java.util.Arrays;

// Конверт — то, что уходит на сервер (в base64). Сервер видит только эти байты:
//   обычное:  0x01 || 0x02 || сообщение храповика
//   первое:   0x01 || 0x01 || EK(32) || spkId(u32) || opkId(u32) || pqId(u32) || [CT(1088), если pqId задан] || сообщение храповика
record Envelope(Offer offer, byte[] message) {
    private static final byte VERSION = 1;
    private static final byte FIRST = 1;
    private static final byte NORMAL = 2;
    private static final int OFFER_FIXED_BYTES = DhKey.KEY_BYTES + 12;

    byte[] encode() {
        if (offer == null) {
            return Bytes.concat(new byte[] {VERSION, NORMAL}, message);
        }
        return Bytes.concat(new byte[] {VERSION, FIRST}, offer.ephemeral(), Bytes.u32(offer.signedId()), Bytes.u32(offer.oneTimeId()),
                Bytes.u32(offer.postQuantumId()), offer.kemCiphertext() == null ? Bytes.EMPTY : offer.kemCiphertext(), message);
    }

    static Envelope decode(byte[] data) throws E2eException {
        if (data.length < 2 || data[0] != VERSION) {
            throw new E2eException("bad_envelope");
        }
        if (data[1] == NORMAL) {
            return new Envelope(null, Arrays.copyOfRange(data, 2, data.length));
        }
        if (data[1] != FIRST || data.length < 2 + OFFER_FIXED_BYTES) {
            throw new E2eException("bad_envelope");
        }
        int offset = 2;
        byte[] ephemeral = Arrays.copyOfRange(data, offset, offset + DhKey.KEY_BYTES);
        offset += DhKey.KEY_BYTES;
        long signedId = Bytes.readU32(data, offset);
        long oneTimeId = Bytes.readU32(data, offset + 4);
        long postQuantumId = Bytes.readU32(data, offset + 8);
        offset += 12;
        byte[] kemCiphertext = null;
        if (postQuantumId != PrekeyBundle.NONE) {
            if (data.length < offset + KemKey.CIPHERTEXT_BYTES) {
                throw new E2eException("bad_envelope");
            }
            kemCiphertext = Arrays.copyOfRange(data, offset, offset + KemKey.CIPHERTEXT_BYTES);
            offset += KemKey.CIPHERTEXT_BYTES;
        }
        return new Envelope(new Offer(ephemeral, signedId, oneTimeId, postQuantumId, kemCiphertext), Arrays.copyOfRange(data, offset, data.length));
    }
}
