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
] as const;

export type SiteContentSection = (typeof SITE_CONTENT_SECTIONS)[number];

export const WEEK_DAYS = [
  'monday',
  'tuesday',
  'wednesday',
  'thursday',
  'friday',
  'saturday',
  'sunday',
] as const;

export type WeekDay = (typeof WEEK_DAYS)[number];

export const WEEK_DAY_LABELS: Record<WeekDay, string> = {
  monday: 'Monday',
  tuesday: 'Tuesday',
  wednesday: 'Wednesday',
  thursday: 'Thursday',
  friday: 'Friday',
  saturday: 'Saturday',
  sunday: 'Sunday',
};

export interface OpeningHours {
  opens: string;
  closes: string;
}

export interface HoursContent {
  days: Record<WeekDay, OpeningHours | null>;
  note: string;
}

export interface ContactContent {
  phone: string;
  email: string;
}

export interface AddressContent {
  street: string;
  area: string;
  town: string;
  province: string;
  postalCode: string;
  country: string;
  landmark: string;
  mapUrl: string;
}

export interface Photo {
  src: string;
  alt: string;
}

export interface PaymentsContent {
  methods: string[];
  qrCode: Photo | null;
}

export interface NoticeContent {
  text: string;
}

export interface SocialContent {
  facebook: string;
  facebookPhotos: string;
  tiktok: string;
}

export interface RiderPartner {
  name: string;
  href: string;
  logo: string;
}

export interface RidersContent {
  partners: RiderPartner[];
}

export interface CatRule {
  title: string;
  text: string;
}

export interface AboutContent {
  openedOn: string;
  heroTitle: string;
  heroText: string;
  homeTitle: string;
  homeText: string;
  homePhoto: Photo;
  intro: string;
  storyTitle: string;
  story: string[];
  photos: Photo[];
  menuSummary: string;
  footerText: string;
  catRules: CatRule[];
}

export interface GalleryPhoto extends Photo {
  featured?: boolean;
  landscape?: boolean;
  people?: boolean;
}

export interface GalleryContent {
  photos: GalleryPhoto[];
}

export interface SiteContent {
  hours: HoursContent;
  contact: ContactContent;
  address: AddressContent;
  payments: PaymentsContent;
  notice: NoticeContent;
  social: SocialContent;
  riders: RidersContent;
  about: AboutContent;
  gallery: GalleryContent;
}

export interface SiteContentRow {
  section: string;
  content: unknown;
  updated_at?: string;
}

export const LIMITS = {
  short: 80,
  medium: 300,
  long: 1000,
  url: 500,
  image: 200,
} as const;

const SPACE =
  ' \\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff';
const EDGE_SPACE = new RegExp(`^[${SPACE}]|[${SPACE}]$`);
const URL_SHAPE = new RegExp(
  `^https://[^${SPACE}/?#]+\\.[^${SPACE}/?#]+([/?#][^${SPACE}]*)?$`,
);
const TIME = /^([01][0-9]|2[0-3]):[0-5][0-9]$/;
const DATE = /^20[0-9]{2}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/;
const IMAGE =
  /^\/images\/[A-Za-z0-9][A-Za-z0-9/_.-]*\.(jpg|jpeg|png|webp|svg)$/;
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/;
const PHONE = /^\+?[0-9][0-9 ()-]*[0-9]$/;
const POSTAL_CODE = /^[0-9]{4}$/;
const CONTROL = /[\u0000-\u001f\u007f]/;

type JsonObject = Record<string, unknown>;

const isObject = (value: unknown): value is JsonObject =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

function hasKeys(
  value: unknown,
  required: readonly string[],
  optional: readonly string[] = [],
): value is JsonObject {
  if (!isObject(value)) return false;
  return (
    required.every((key) => Object.prototype.hasOwnProperty.call(value, key)) &&
    Object.keys(value).every(
      (key) => required.includes(key) || optional.includes(key),
    )
  );
}

const codePointLength = (text: string) => Array.from(text).length;

export function isText(
  value: unknown,
  max: number,
  { allowEmpty = false } = {},
): value is string {
  return (
    typeof value === 'string' &&
    !EDGE_SPACE.test(value) &&
    !CONTROL.test(value) &&
    codePointLength(value) <= max &&
    (allowEmpty || value.length > 0)
  );
}

export const isUrl = (value: unknown): value is string =>
  isText(value, LIMITS.url) && URL_SHAPE.test(value);

export const isOptionalUrl = (value: unknown): value is string =>
  value === '' || isUrl(value);

export const isImagePath = (value: unknown): value is string =>
  isText(value, LIMITS.image) &&
  IMAGE.test(value) &&
  !value.includes('..') &&
  !value.includes('//');

function isPhoto(value: unknown, optional: readonly string[] = []): boolean {
  return (
    hasKeys(value, ['src', 'alt'], optional) &&
    isImagePath(value.src) &&
    isText(value.alt, LIMITS.medium)
  );
}

function isList(
  value: unknown,
  min: number,
  max: number,
  check: (entry: unknown) => boolean,
): value is unknown[] {
  return (
    Array.isArray(value) &&
    value.length >= min &&
    value.length <= max &&
    value.every(check)
  );
}

function isRealDate(value: unknown): value is string {
  if (typeof value !== 'string' || !DATE.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}

const digitCount = (text: string) => text.replace(/[^0-9]/g, '').length;

export function validateSiteContentSection<S extends SiteContentSection>(
  section: S,
  value: unknown,
): { ok: true; value: SiteContent[S] } | { ok: false; error: string } {
  switch (section) {
    case 'hours': {
      if (!hasKeys(value, ['days', 'note']) || !hasKeys(value.days, WEEK_DAYS)) {
        return { ok: false, error: 'Hours must include Monday through Sunday and a schedule note.' };
      }
      for (const day of WEEK_DAYS) {
        const hours = (value.days as JsonObject)[day];
        if (hours === null) continue;
        if (
          !hasKeys(hours, ['opens', 'closes']) ||
          typeof hours.opens !== 'string' ||
          typeof hours.closes !== 'string' ||
          !TIME.test(hours.opens) ||
          !TIME.test(hours.closes) ||
          !(hours.opens < hours.closes)
        ) {
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
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'contact': {
      if (!hasKeys(value, ['phone', 'email'])) {
        return { ok: false, error: 'Contact requires both phone and email.' };
      }
      if (
        !isText(value.phone, 20) ||
        !PHONE.test(value.phone) ||
        digitCount(value.phone) < 7 ||
        digitCount(value.phone) > 15
      ) {
        return {
          ok: false,
          error: 'Phone number must be 7 to 15 digits (spaces, dashes, parentheses, and leading + allowed).',
        };
      }
      if (!isText(value.email, 254) || !EMAIL.test(value.email)) {
        return { ok: false, error: 'Enter a valid contact email address.' };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'address': {
      if (
        !hasKeys(value, [
          'street',
          'area',
          'town',
          'province',
          'postalCode',
          'country',
          'landmark',
          'mapUrl',
        ])
      ) {
        return { ok: false, error: 'Address is missing required fields.' };
      }
      for (const key of ['street', 'area', 'town', 'province', 'country'] as const) {
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
      return { ok: true, value: value as unknown as SiteContent[S] };
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
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'notice': {
      if (
        !hasKeys(value, ['text']) ||
        !isText(value.text, LIMITS.medium, { allowEmpty: true })
      ) {
        return {
          ok: false,
          error: 'Notice text must be one line up to 300 characters without leading or trailing spaces.',
        };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'social': {
      if (!hasKeys(value, ['facebook', 'facebookPhotos', 'tiktok'])) {
        return { ok: false, error: 'Social links require facebook, facebookPhotos, and tiktok.' };
      }
      if (
        !isOptionalUrl(value.facebook) ||
        !isOptionalUrl(value.facebookPhotos) ||
        !isOptionalUrl(value.tiktok)
      ) {
        return {
          ok: false,
          error: 'Each social link must be a valid https:// URL or left empty.',
        };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'riders': {
      if (
        !hasKeys(value, ['partners']) ||
        !isList(
          value.partners,
          0,
          8,
          (partner) =>
            hasKeys(partner, ['name', 'href', 'logo']) &&
            isText(partner.name, LIMITS.short) &&
            isUrl(partner.href) &&
            isImagePath(partner.logo),
        )
      ) {
        return {
          ok: false,
          error: 'Each delivery partner (up to 8) needs a name, an https:// link, and a /images/... logo path.',
        };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'about': {
      if (
        !hasKeys(value, [
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
        ])
      ) {
        return { ok: false, error: 'About content is missing required fields.' };
      }
      if (!isRealDate(value.openedOn)) {
        return { ok: false, error: 'Opening date must be a valid YYYY-MM-DD date.' };
      }
      for (const key of ['heroTitle', 'homeTitle', 'storyTitle', 'menuSummary'] as const) {
        if (!isText(value[key], LIMITS.short)) {
          return { ok: false, error: `${key} must be 1 to 80 characters.` };
        }
      }
      for (const key of ['heroText', 'homeText', 'intro', 'footerText'] as const) {
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
      if (
        !isList(
          value.catRules,
          0,
          6,
          (rule) =>
            hasKeys(rule, ['title', 'text']) &&
            isText(rule.title, LIMITS.short) &&
            isText(rule.text, LIMITS.medium),
        )
      ) {
        return { ok: false, error: 'Cat rules (up to 6) each require a title (≤80 chars) and text (≤300 chars).' };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }

    case 'gallery': {
      if (
        !hasKeys(value, ['photos']) ||
        !isList(
          value.photos,
          1,
          80,
          (photo) =>
            isPhoto(photo, ['featured', 'landscape', 'people']) &&
            ['featured', 'landscape', 'people'].every(
              (flag) =>
                !Object.prototype.hasOwnProperty.call(photo as JsonObject, flag) ||
                typeof (photo as JsonObject)[flag] === 'boolean',
            ),
        )
      ) {
        return {
          ok: false,
          error: 'Gallery must contain 1 to 80 photos with valid /images/... paths and alt text.',
        };
      }
      return { ok: true, value: value as unknown as SiteContent[S] };
    }
  }
}

export interface TakeoutBoxMenuItemLike {
  id: string;
  name: string;
  variants_json: string;
}

export function isTakeoutBoxName(name: string): boolean {
  return name.toLowerCase().replace(/[^a-z]/g, '') === 'takeoutbox';
}

export function findTakeoutBoxItem<T extends TakeoutBoxMenuItemLike>(
  items: readonly T[],
): T | null {
  return items.find((item) => isTakeoutBoxName(item.name)) ?? null;
}

export function getTakeoutBoxPesos(item: TakeoutBoxMenuItemLike | null): number | null {
  if (!item) return null;
  try {
    const variants = JSON.parse(item.variants_json);
    if (!Array.isArray(variants) || variants.length === 0) return null;
    const base = Number(variants[0]?.basePrice);
    return Number.isFinite(base) && base > 0 ? base : null;
  } catch {
    return null;
  }
}

export function updateTakeoutBoxVariantsJson(
  variantsJson: string,
  newPesos: number,
): string {
  const price = Math.round(newPesos);
  try {
    const parsed = JSON.parse(variantsJson);
    if (Array.isArray(parsed) && parsed.length > 0) {
      const updated = parsed.map((v, idx) =>
        idx === 0 ? { ...v, basePrice: price, priceByFlavor: {} } : v,
      );
      return JSON.stringify(updated);
    }
  } catch {
    // fall through to default variant
  }
  return JSON.stringify([
    { id: 'regular', name: 'Regular', basePrice: price, priceByFlavor: {} },
  ]);
}
