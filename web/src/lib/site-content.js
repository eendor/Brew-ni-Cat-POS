export const SITE_CONTENT_SECTIONS = [
    'hours',
    'contact',
    'address',
    'payments',
    'notice',
    'social',
    'riders',
    'about',
    'gallery',
];
export const WEEK_DAYS = [
    'monday',
    'tuesday',
    'wednesday',
    'thursday',
    'friday',
    'saturday',
    'sunday',
];
export const WEEK_DAY_LABELS = {
    monday: 'Monday',
    tuesday: 'Tuesday',
    wednesday: 'Wednesday',
    thursday: 'Thursday',
    friday: 'Friday',
    saturday: 'Saturday',
    sunday: 'Sunday',
};
export const LIMITS = {
    short: 80,
    medium: 300,
    long: 1000,
    url: 500,
    image: 200,
};
const SPACE = ' \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff';
const EDGE_SPACE = new RegExp(`^[${SPACE}]|[${SPACE}]$`);
const URL_SHAPE = new RegExp(`^https://[^${SPACE}/?#]+\\.[^${SPACE}/?#]+([/?#][^${SPACE}]*)?$`);
const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
const DATE = /^20[0-9]{2}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/;
const IMAGE = /^\/images\/[A-Za-z0-9][A-Za-z0-9/_.-]*\.(jpg|jpeg|png|webp|svg)$/;
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;
const PHONE = /^\+?[0-9][0-9 ()-]*[0-9]$/;
const POSTAL_CODE = /^[0-9]{4}$/;
const CONTROL = /[\u0000-\u001f\u007f]/;
const isObject = (value) => typeof value === 'object' && value !== null && !Array.isArray(value);
function hasKeys(value, required, optional = []) {
    if (!isObject(value))
        return false;
    return (required.every((key) => Object.prototype.hasOwnProperty.call(value, key)) &&
        Object.keys(value).every((key) => required.includes(key) || optional.includes(key)));
}
const codePointLength = (text) => Array.from(text).length;
export function isText(value, max, { allowEmpty = false } = {}) {
    return (typeof value === 'string' &&
        !EDGE_SPACE.test(value) &&
        !CONTROL.test(value) &&
        codePointLength(value) <= max &&
        (allowEmpty || value.length > 0));
}
export const isUrl = (value) => isText(value, LIMITS.url) && URL_SHAPE.test(value);
export const isOptionalUrl = (value) => value === '' || isUrl(value);
export const isImagePath = (value) => isText(value, LIMITS.image) &&
    IMAGE.test(value) &&
    !value.includes('..') &&
    !value.includes('//');
function isPhoto(value, optional = []) {
    return (hasKeys(value, ['src', 'alt'], optional) &&
        isImagePath(value.src) &&
        isText(value.alt, LIMITS.medium));
}
function isList(value, min, max, check) {
    return (Array.isArray(value) &&
        value.length >= min &&
        value.length <= max &&
        value.every(check));
}
function isRealDate(value) {
    if (typeof value !== 'string' || !DATE.test(value))
        return false;
    const date = new Date(`${value}T00:00:00Z`);
    return (!Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value);
}
const digitCount = (text) => text.replace(/[^0-9]/g, '').length;
export function validateSiteContentSection(section, value) {
    switch (section) {
        case 'hours': {
            if (!hasKeys(value, ['days', 'note']) || !hasKeys(value.days, WEEK_DAYS)) {
                return { ok: false, error: 'Hours must include Monday through Sunday and a schedule note.' };
            }
            for (const day of WEEK_DAYS) {
                const hours = value.days[day];
                if (hours === null)
                    continue;
                if (!hasKeys(hours, ['opens', 'closes']) ||
                    typeof hours.opens !== 'string' ||
                    typeof hours.closes !== 'string' ||
                    !TIME.test(hours.opens) ||
                    !TIME.test(hours.closes) ||
                    !(hours.opens < hours.closes)) {
                    return {
                        ok: false,
                        error: `${WEEK_DAY_LABELS[day]} must be closed or use 24-hour HH:MM times where opening is before closing.`,
                    };
                }
            }
            if (!isText(value.note, LIMITS.medium, { allowEmpty: true })) {
                return {
                    ok: false,
                    error: 'Schedule note must be one line up to 300 characters without leading or trailing spaces.',
                };
            }
            return { ok: true, value: value };
        }
        case 'contact': {
            if (!hasKeys(value, ['phone', 'email'])) {
                return { ok: false, error: 'Contact requires both phone and email.' };
            }
            if (!isText(value.phone, 20) ||
                !PHONE.test(value.phone) ||
                digitCount(value.phone) < 7 ||
                digitCount(value.phone) > 15) {
                return {
                    ok: false,
                    error: 'Phone number must be 7 to 15 digits (spaces, dashes, parentheses, and leading + allowed).',
                };
            }
            if (!isText(value.email, 254) || !EMAIL.test(value.email)) {
                return { ok: false, error: 'Enter a valid contact email address.' };
            }
            return { ok: true, value: value };
        }
        case 'address': {
            if (!hasKeys(value, [
                'street',
                'area',
                'town',
                'province',
                'postalCode',
                'country',
                'landmark',
                'mapUrl',
            ])) {
                return { ok: false, error: 'Address is missing required fields.' };
            }
            for (const key of ['street', 'area', 'town', 'province', 'country']) {
                if (!isText(value[key], LIMITS.short)) {
                    return {
                        ok: false,
                        error: `Address ${key} must be 1 to 80 characters without leading or trailing spaces.`,
                    };
                }
            }
            if (typeof value.postalCode !== 'string' || !POSTAL_CODE.test(value.postalCode)) {
                return { ok: false, error: 'Postal code must be exactly 4 digits (e.g. 9407).' };
            }
            if (!isText(value.landmark, LIMITS.short * 2, { allowEmpty: true })) {
                return { ok: false, error: 'Landmark must be up to 160 characters without leading or trailing spaces.' };
            }
            if (!isUrl(value.mapUrl)) {
                return { ok: false, error: 'Map link must be a valid https:// URL.' };
            }
            return { ok: true, value: value };
        }
        case 'payments': {
            if (!hasKeys(value, ['methods', 'qrCode'])) {
                return { ok: false, error: 'Payments requires methods and qrCode.' };
            }
            if (!isList(value.methods, 1, 6, (m) => isText(m, 30))) {
                return {
                    ok: false,
                    error: 'List 1 to 6 payment methods (each 1 to 30 characters).',
                };
            }
            if (new Set(value.methods).size !== value.methods.length) {
                return { ok: false, error: 'Payment methods must not contain duplicates.' };
            }
            if (value.qrCode !== null && !isPhoto(value.qrCode)) {
                return {
                    ok: false,
                    error: 'QR code must be empty or have a /images/... path and description.',
                };
            }
            return { ok: true, value: value };
        }
        case 'notice': {
            if (!hasKeys(value, ['text']) ||
                !isText(value.text, LIMITS.medium, { allowEmpty: true })) {
                return {
                    ok: false,
                    error: 'Notice text must be one line up to 300 characters without leading or trailing spaces.',
                };
            }
            return { ok: true, value: value };
        }
        case 'social': {
            if (!hasKeys(value, ['facebook', 'facebookPhotos', 'tiktok'])) {
                return { ok: false, error: 'Social links require facebook, facebookPhotos, and tiktok.' };
            }
            if (!isOptionalUrl(value.facebook) ||
                !isOptionalUrl(value.facebookPhotos) ||
                !isOptionalUrl(value.tiktok)) {
                return {
                    ok: false,
                    error: 'Each social link must be a valid https:// URL or left empty.',
                };
            }
            return { ok: true, value: value };
        }
        case 'riders': {
            if (!hasKeys(value, ['partners']) ||
                !isList(value.partners, 0, 8, (partner) => hasKeys(partner, ['name', 'href', 'logo']) &&
                    isText(partner.name, LIMITS.short) &&
                    isUrl(partner.href) &&
                    isImagePath(partner.logo))) {
                return {
                    ok: false,
                    error: 'Each delivery partner (up to 8) needs a name, an https:// link, and a /images/... logo path.',
                };
            }
            return { ok: true, value: value };
        }
        case 'about': {
            if (!hasKeys(value, [
                'openedOn',
                'heroTitle',
                'heroText',
                'homeTitle',
                'homeText',
                'homePhoto',
                'intro',
                'storyTitle',
                'story',
                'photos',
                'menuSummary',
                'footerText',
                'catRules',
            ])) {
                return { ok: false, error: 'About content is missing required fields.' };
            }
            if (!isRealDate(value.openedOn)) {
                return { ok: false, error: 'Opening date must be a valid YYYY-MM-DD date.' };
            }
            for (const key of ['heroTitle', 'homeTitle', 'storyTitle', 'menuSummary']) {
                if (!isText(value[key], LIMITS.short)) {
                    return { ok: false, error: `${key} must be 1 to 80 characters.` };
                }
            }
            for (const key of ['heroText', 'homeText', 'intro', 'footerText']) {
                if (!isText(value[key], LIMITS.medium)) {
                    return { ok: false, error: `${key} must be 1 to 300 characters.` };
                }
            }
            if (!isPhoto(value.homePhoto)) {
                return { ok: false, error: 'Home photo needs a valid /images/... path and alt text.' };
            }
            if (!isList(value.story, 1, 6, (p) => isText(p, LIMITS.long))) {
                return { ok: false, error: 'Story must have 1 to 6 paragraphs (up to 1000 characters each).' };
            }
            if (!isList(value.photos, 3, 3, (p) => isPhoto(p))) {
                return { ok: false, error: 'About page requires exactly 3 photos with /images/... paths and alt text.' };
            }
            if (!isList(value.catRules, 0, 6, (rule) => hasKeys(rule, ['title', 'text']) &&
                isText(rule.title, LIMITS.short) &&
                isText(rule.text, LIMITS.medium))) {
                return { ok: false, error: 'Cat rules (up to 6) each require a title (≤80 chars) and text (≤300 chars).' };
            }
            return { ok: true, value: value };
        }
        case 'gallery': {
            if (!hasKeys(value, ['photos']) ||
                !isList(value.photos, 1, 80, (photo) => isPhoto(photo, ['featured', 'landscape', 'people']) &&
                    ['featured', 'landscape', 'people'].every((flag) => !Object.prototype.hasOwnProperty.call(photo, flag) ||
                        typeof photo[flag] === 'boolean'))) {
                return {
                    ok: false,
                    error: 'Gallery must contain 1 to 80 photos with valid /images/... paths and alt text.',
                };
            }
            return { ok: true, value: value };
        }
    }
}
export function isTakeoutBoxName(name) {
    return name.toLowerCase().replace(/[^a-z]/g, '') === 'takeoutbox';
}
export function findTakeoutBoxItem(items) {
    return items.find((item) => isTakeoutBoxName(item.name)) ?? null;
}
export function getTakeoutBoxPesos(item) {
    if (!item)
        return null;
    try {
        const variants = JSON.parse(item.variants_json);
        if (!Array.isArray(variants) || variants.length === 0)
            return null;
        const base = Number(variants[0]?.basePrice);
        return Number.isFinite(base) && base > 0 ? base : null;
    }
    catch {
        return null;
    }
}
export function updateTakeoutBoxVariantsJson(variantsJson, newPesos) {
    const price = Math.round(newPesos);
    try {
        const parsed = JSON.parse(variantsJson);
        if (Array.isArray(parsed) && parsed.length > 0) {
            const updated = parsed.map((v, idx) => idx === 0 ? { ...v, basePrice: price, priceByFlavor: {} } : v);
            return JSON.stringify(updated);
        }
    }
    catch {
        // fall through to default variant
    }
    return JSON.stringify([
        { id: 'regular', name: 'Regular', basePrice: price, priceByFlavor: {} },
    ]);
}
