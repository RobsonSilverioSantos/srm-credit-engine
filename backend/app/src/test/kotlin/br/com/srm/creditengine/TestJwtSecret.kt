package br.com.srm.creditengine

import java.util.UUID

object TestJwtSecret {
    val value: String = UUID.randomUUID().toString() + UUID.randomUUID().toString()
}
