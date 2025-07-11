package com.artemchep.keyguard.provider.bitwarden.usecase

import com.artemchep.keyguard.common.io.IO
import com.artemchep.keyguard.common.io.flatten
import com.artemchep.keyguard.common.io.ioEffect
import com.artemchep.keyguard.common.io.map
import com.artemchep.keyguard.common.model.DWatchtowerAlertType
import com.artemchep.keyguard.common.model.PatchWatchtowerAlertCipherRequest
import com.artemchep.keyguard.common.usecase.PatchWatchtowerAlertCipher
import com.artemchep.keyguard.core.store.bitwarden.BitwardenCipher
import com.artemchep.keyguard.core.store.bitwarden.ignoredAlerts
import com.artemchep.keyguard.provider.bitwarden.usecase.util.ModifyCipherById
import kotlin.time.Clock
import org.kodein.di.DirectDI
import org.kodein.di.instance

/**
 * @author Artem Chepurnyi
 */
class PatchWatchtowerAlertCipherImpl(
    private val modifyCipherById: ModifyCipherById,
) : PatchWatchtowerAlertCipher {
    companion object {
        private const val TAG = "PatchWatchtowerAlertCipher.bitwarden"
    }

    constructor(directDI: DirectDI) : this(
        modifyCipherById = directDI.instance(),
    )

    override fun invoke(
        request: PatchWatchtowerAlertCipherRequest,
    ): IO<Boolean> = ioEffect {
        val createdAt = Clock.System.now()
        val cipherIds = request.patch.keys
        modifyCipherById(
            cipherIds,
            updateRevisionDate = false,
        ) { model ->
            var new = model
            val r = request.patch[model.cipherId]
                ?: return@modifyCipherById new

            val oldIgnoreAlerts = model.data_.ignoredAlerts
            val newIgnoreAlerts = oldIgnoreAlerts
                .toMutableMap()
            r.entries.forEach { entry ->
                val key = when (entry.key) {
                    DWatchtowerAlertType.WEAK_PASSWORD -> null
                    DWatchtowerAlertType.REUSED_PASSWORD -> BitwardenCipher.IgnoreAlertType.REUSED_PASSWORD
                    DWatchtowerAlertType.PWNED_PASSWORD -> BitwardenCipher.IgnoreAlertType.PWNED_PASSWORD
                    DWatchtowerAlertType.PWNED_WEBSITE -> BitwardenCipher.IgnoreAlertType.PWNED_WEBSITE
                    DWatchtowerAlertType.UNSECURE_WEBSITE -> BitwardenCipher.IgnoreAlertType.UNSECURE_WEBSITE
                    DWatchtowerAlertType.TWO_FA_WEBSITE -> BitwardenCipher.IgnoreAlertType.TWO_FA_WEBSITE
                    DWatchtowerAlertType.PASSKEY_WEBSITE -> BitwardenCipher.IgnoreAlertType.PASSKEY_WEBSITE
                    DWatchtowerAlertType.DUPLICATE -> BitwardenCipher.IgnoreAlertType.DUPLICATE
                    DWatchtowerAlertType.DUPLICATE_URIS -> BitwardenCipher.IgnoreAlertType.DUPLICATE_URIS
                    DWatchtowerAlertType.BROAD_URIS -> BitwardenCipher.IgnoreAlertType.BROAD_URIS
                    DWatchtowerAlertType.INCOMPLETE -> BitwardenCipher.IgnoreAlertType.INCOMPLETE
                    DWatchtowerAlertType.EXPIRING -> BitwardenCipher.IgnoreAlertType.EXPIRING
                }
                    ?: return@forEach
                val shouldIgnore = entry.value
                if (shouldIgnore) {
                    newIgnoreAlerts[key] = BitwardenCipher.IgnoreAlertData(
                        createdAt = createdAt,
                    )
                } else {
                    newIgnoreAlerts.remove(key)
                }
            }

            new = new.copy(
                data_ = BitwardenCipher.ignoredAlerts.set(new.data_, newIgnoreAlerts),
            )
            new
        }
            // Report that we have actually modified the
            // ciphers.
            .map { changedCipherIds ->
                request.patch.keys
                    .intersect(changedCipherIds)
                    .isNotEmpty()
            }
    }
        .flatten()
}
