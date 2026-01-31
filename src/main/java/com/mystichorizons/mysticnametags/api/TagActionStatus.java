package com.mystichorizons.mysticnametags.api;

import com.mystichorizons.mysticnametags.tags.TagManager;

/**
 * High-level result for tag actions (toggle, purchase + equip, etc.).
 *
 * This intentionally wraps {@link TagManager.TagPurchaseResult} so the
 * internal enum can change without breaking external plugins.
 */
public enum TagActionStatus {

    // Success cases
    SUCCESS_EQUIPPED_ALREADY_OWNED,
    SUCCESS_UNLOCKED_FREE,
    SUCCESS_UNLOCKED_PAID,
    SUCCESS_UNEQUIPPED,

    // Failure cases
    FAILED_NOT_FOUND,
    FAILED_NO_PERMISSION,
    FAILED_NO_ECONOMY,
    FAILED_NOT_ENOUGH_MONEY,
    FAILED_TRANSACTION;

    public boolean isSuccess() {
        switch (this) {
            case SUCCESS_EQUIPPED_ALREADY_OWNED:
            case SUCCESS_UNLOCKED_FREE:
            case SUCCESS_UNLOCKED_PAID:
            case SUCCESS_UNEQUIPPED:
                return true;
            default:
                return false;
        }
    }

    public static TagActionStatus fromInternal(TagManager.TagPurchaseResult result) {
        if (result == null) {
            return FAILED_TRANSACTION;
        }

        switch (result) {
            case EQUIPPED_ALREADY_OWNED:
                return SUCCESS_EQUIPPED_ALREADY_OWNED;
            case UNLOCKED_FREE:
                return SUCCESS_UNLOCKED_FREE;
            case UNLOCKED_PAID:
                return SUCCESS_UNLOCKED_PAID;
            case UNEQUIPPED:
                return SUCCESS_UNEQUIPPED;

            case NOT_FOUND:
                return FAILED_NOT_FOUND;
            case NO_PERMISSION:
                return FAILED_NO_PERMISSION;
            case NO_ECONOMY:
                return FAILED_NO_ECONOMY;
            case NOT_ENOUGH_MONEY:
                return FAILED_NOT_ENOUGH_MONEY;
            case TRANSACTION_FAILED:
            default:
                return FAILED_TRANSACTION;
        }
    }
}
