package dev.no.name.blooddesigner.net.e2e;

// Что инициатор прикладывает к первым сообщениям, чтобы получатель вывел тот же ключ сессии:
// свой одноразовый ключ, номера использованных предключей и шифротекст ML-KEM (null, если без него)
record Offer(byte[] ephemeral, long signedId, long oneTimeId, long postQuantumId, byte[] kemCiphertext) {
}
