package dev.carlsen.mega.extension

import com.ionspin.kotlin.bignum.integer.BigInteger

fun BigInteger.modPow(exponent: BigInteger, modulus: BigInteger): BigInteger {
    var result = BigInteger.ONE
    var baseMod = this % modulus
    var exp = exponent

    while (exp > BigInteger.ZERO) {
        if (exp % BigInteger.TWO == BigInteger.ONE) {
            result = (result * baseMod) % modulus
        }
        exp /= BigInteger.TWO
        baseMod = (baseMod * baseMod) % modulus
    }

    return result
}