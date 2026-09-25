<?php declare(strict_types=1);

namespace Mmo\Auth;

final class GuestName
{
    public static function normalize(mixed $value): ?string
    {
        if (!is_string($value) || preg_match('//u', $value) !== 1) {
            return null;
        }
        $collapsed = preg_replace('/\s+/u', ' ', trim($value));
        if (!is_string($collapsed) || preg_match('/[\p{L}\p{N}_ -]+/u', $collapsed) !== 1) {
            return null;
        }
        if (preg_match('/[\p{C}]/u', $collapsed) === 1) {
            return null;
        }
        $characters = preg_split('//u', $collapsed, -1, PREG_SPLIT_NO_EMPTY);
        if (!is_array($characters) || count($characters) < 2 || count($characters) > 24) {
            return null;
        }

        return implode('', $characters);
    }
}
