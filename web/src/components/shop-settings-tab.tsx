'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  Clock,
  MapPin,
  Phone,
  CreditCard,
  Megaphone,
  Share2,
  Truck,
  BookOpen,
  Image as ImageIcon,
  Save,
  Plus,
  Trash2,
  CheckCircle2,
  AlertCircle,
  RefreshCw,
  ShieldCheck,
  Package,
} from 'lucide-react';
import {
  SITE_CONTENT_SECTIONS,
  WEEK_DAYS,
  WEEK_DAY_LABELS,
  validateSiteContentSection,
  findTakeoutBoxItem,
  getTakeoutBoxPesos,
  updateTakeoutBoxVariantsJson,
  type SiteContent,
  type SiteContentSection,
  type WeekDay,
  type TakeoutBoxMenuItemLike,
} from '@/lib/site-content';

interface ShopSettingsTabProps {
  supabase: any;
  menuItems: TakeoutBoxMenuItemLike[];
  onMenuItemsChanged: () => Promise<void> | void;
}

type SettingsGroup = 'hours' | 'contact' | 'channels' | 'about' | 'gallery';

export function ShopSettingsTab({
  supabase,
  menuItems,
  onMenuItemsChanged,
}: ShopSettingsTabProps) {
  const [activeGroup, setActiveGroup] = useState<SettingsGroup>('hours');
  const [content, setContent] = useState<Partial<SiteContent>>({});
  const [updatedAt, setUpdatedAt] = useState<Partial<Record<SiteContentSection, string>>>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [savingSection, setSavingSection] = useState<string | null>(null);
  const [sectionFeedback, setSectionFeedback] = useState<
    Record<string, { type: 'ok' | 'err'; message: string }>
  >({});

  const takeoutItem = findTakeoutBoxItem(menuItems);
  const currentTakeoutPesos = getTakeoutBoxPesos(takeoutItem);
  const [takeoutFeeInput, setTakeoutFeeInput] = useState<string>(
    currentTakeoutPesos !== null ? String(currentTakeoutPesos) : '',
  );

  useEffect(() => {
    if (currentTakeoutPesos !== null) {
      setTakeoutFeeInput(String(currentTakeoutPesos));
    }
  }, [currentTakeoutPesos]);

  const loadSiteContent = useCallback(async () => {
    if (!supabase) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const { data, error } = await supabase
        .from('site_content')
        .select('section, content, updated_at');
      if (error) throw error;
      const nextContent: Partial<SiteContent> = {};
      const nextUpdated: Partial<Record<SiteContentSection, string>> = {};
      for (const row of data || []) {
        const sec = row.section as SiteContentSection;
        if ((SITE_CONTENT_SECTIONS as readonly string[]).includes(sec)) {
          (nextContent as any)[sec] = row.content;
          if (row.updated_at) nextUpdated[sec] = row.updated_at;
        }
      }
      setContent(nextContent);
      setUpdatedAt(nextUpdated);
    } catch (e: any) {
      setLoadError(
        e?.message || 'Could not load website content from public.site_content.',
      );
    } finally {
      setLoading(false);
    }
  }, [supabase]);

  useEffect(() => {
    loadSiteContent();
  }, [loadSiteContent]);

  const saveSection = async <S extends SiteContentSection>(section: S) => {
    const draft = content[section];
    if (!draft) return;

    const checked = validateSiteContentSection(section, draft);
    if (!checked.ok) {
      setSectionFeedback((prev) => ({
        ...prev,
        [section]: { type: 'err', message: checked.error },
      }));
      return;
    }

    setSavingSection(section);
    setSectionFeedback((prev) => {
      const copy = { ...prev };
      delete copy[section];
      return copy;
    });

    try {
      const { data, error } = await supabase
        .from('site_content')
        .update({ content: checked.value })
        .eq('section', section)
        .select('updated_at')
        .single();
      if (error) throw error;
      if (data?.updated_at) {
        setUpdatedAt((prev) => ({ ...prev, [section]: data.updated_at }));
      }
      setSectionFeedback((prev) => ({
        ...prev,
        [section]: {
          type: 'ok',
          message:
            'Saved and recorded in the audit log. Live on the website within 60 seconds.',
        },
      }));
    } catch (e: any) {
      setSectionFeedback((prev) => ({
        ...prev,
        [section]: {
          type: 'err',
          message: e?.message || `Failed to save ${section}.`,
        },
      }));
    } finally {
      setSavingSection(null);
    }
  };

  const saveTakeoutBoxFee = async () => {
    if (!takeoutItem) {
      setSectionFeedback((prev) => ({
        ...prev,
        takeout: {
          type: 'err',
          message: 'No Take-out Box item found in the menu catalog.',
        },
      }));
      return;
    }
    const parsed = Number(takeoutFeeInput);
    if (!Number.isFinite(parsed) || parsed <= 0 || Math.round(parsed) !== parsed) {
      setSectionFeedback((prev) => ({
        ...prev,
        takeout: {
          type: 'err',
          message: 'Take-out box fee must be a whole peso amount above ₱0.',
        },
      }));
      return;
    }

    setSavingSection('takeout');
    setSectionFeedback((prev) => {
      const copy = { ...prev };
      delete copy.takeout;
      return copy;
    });

    try {
      const variants_json = updateTakeoutBoxVariantsJson(
        takeoutItem.variants_json,
        parsed,
      );
      const { error } = await supabase
        .from('items')
        .update({ variants_json })
        .eq('id', takeoutItem.id);
      if (error) throw error;
      await onMenuItemsChanged();
      setSectionFeedback((prev) => ({
        ...prev,
        takeout: {
          type: 'ok',
          message: `Saved Take-out Box fee (₱${parsed}).`,
        },
      }));
    } catch (e: any) {
      setSectionFeedback((prev) => ({
        ...prev,
        takeout: {
          type: 'err',
          message: e?.message || 'Failed to update Take-out Box price.',
        },
      }));
    } finally {
      setSavingSection(null);
    }
  };

  const formatTimestamp = (iso?: string) => {
    if (!iso) return 'Not saved yet';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return 'Saved';
    return `Updated ${d.toLocaleDateString([], {
      month: 'short',
      day: 'numeric',
    })} at ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  };

  const renderFeedback = (key: string) => {
    const fb = sectionFeedback[key];
    if (!fb) return null;
    return (
      <div
        role={fb.type === 'err' ? 'alert' : 'status'}
        className={`flex items-center gap-2 px-3.5 py-2.5 rounded-2xl text-xs font-semibold border ${
          fb.type === 'ok'
            ? 'bg-emerald-500/10 border-emerald-500/25 text-emerald-400'
            : 'bg-red-500/10 border-red-500/25 text-red-400'
        }`}
      >
        {fb.type === 'ok' ? (
          <CheckCircle2 className="w-4 h-4 shrink-0" />
        ) : (
          <AlertCircle className="w-4 h-4 shrink-0" />
        )}
        <span>{fb.message}</span>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="bg-surface border border-hair rounded-3xl p-10 flex items-center justify-center gap-3">
        <RefreshCw className="w-5 h-5 text-emerald-400 animate-spin" />
        <span className="text-xs font-bold text-txt-2 uppercase tracking-wider">
          Loading website settings…
        </span>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="bg-surface border border-hair rounded-3xl p-8 flex flex-col gap-4">
        <div className="flex items-center gap-2.5 text-red-400">
          <AlertCircle className="w-5 h-5" />
          <h3 className="text-sm font-black">Website content unavailable</h3>
        </div>
        <p className="text-xs text-txt-2 leading-relaxed">{loadError}</p>
        <div>
          <button
            onClick={loadSiteContent}
            className="px-4 py-2.5 min-h-[44px] bg-surface-2 hover:bg-surface-3 border border-hair rounded-full text-xs font-bold text-txt-1 transition-colors"
          >
            Try again
          </button>
        </div>
      </div>
    );
  }

  const hours = content.hours;
  const notice = content.notice;
  const contact = content.contact;
  const address = content.address;
  const payments = content.payments;
  const social = content.social;
  const riders = content.riders;
  const about = content.about;
  const gallery = content.gallery;

  return (
    <div className="flex flex-col gap-6">
      {/* Top summary bar & section switcher */}
      <div className="bg-surface border border-hair rounded-3xl p-5 sm:p-6 flex flex-col gap-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-black text-txt-1">
                Owner Website &amp; Shop Settings
              </h3>
            </div>
            <p className="text-xs text-txt-3 leading-relaxed">
              Changes made here update the public website within a minute without a
              redeploy. Every update is validated and recorded in the audit log.
            </p>
          </div>
          <button
            onClick={loadSiteContent}
            className="self-start sm:self-auto inline-flex items-center gap-2 px-4 py-2.5 min-h-[44px] bg-surface-2 hover:bg-surface-3 border border-hair rounded-full text-xs font-bold text-txt-2 hover:text-txt-1 transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Reload saved values
          </button>
        </div>

        <div className="apple-segment flex flex-wrap gap-1">
          {(
            [
              { id: 'hours', label: 'Hours & Notice', Icon: Clock },
              { id: 'contact', label: 'Contact, Address & Fees', Icon: MapPin },
              { id: 'channels', label: 'Social & Riders', Icon: Share2 },
              { id: 'about', label: 'About & Home Copy', Icon: BookOpen },
              { id: 'gallery', label: 'Gallery Photos', Icon: ImageIcon },
            ] as const
          ).map(({ id, label, Icon }) => (
            <button
              key={id}
              onClick={() => setActiveGroup(id)}
              className={`flex-1 min-w-[140px] min-h-[44px] flex items-center justify-center gap-2 px-3 py-2 rounded-xl text-xs font-bold transition-all ${
                activeGroup === id
                  ? 'bg-surface text-emerald-400 shadow-sm border border-hair'
                  : 'text-txt-3 hover:text-txt-1'
              }`}
            >
              <Icon className="w-3.5 h-3.5 shrink-0" />
              <span>{label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* GROUP 1: HOURS & SITE NOTICE */}
      {activeGroup === 'hours' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Site-wide notice */}
          {notice && (
            <section className="lg:col-span-1 bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4 h-fit">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Megaphone className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">Site Banner Notice</h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.notice)}
                </span>
              </div>
              <p className="text-xs text-txt-3 leading-relaxed">
                Shown above every page on the website. Leave blank to hide the banner.
              </p>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-txt-2">
                  Banner text (optional, up to 300 chars)
                </span>
                <textarea
                  rows={3}
                  maxLength={300}
                  value={notice.text}
                  onChange={(e) =>
                    setContent((prev) => ({
                      ...prev,
                      notice: { text: e.target.value.replace(/[\r\n]+/g, ' ') },
                    }))
                  }
                  placeholder="e.g. Special holiday hours this Friday: 1 PM – 8 PM"
                  className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                />
              </label>
              {renderFeedback('notice')}
              <button
                onClick={() => saveSection('notice')}
                disabled={savingSection === 'notice'}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
              >
                <Save className="w-3.5 h-3.5" />
                {savingSection === 'notice' ? 'Saving…' : 'Save notice'}
              </button>
            </section>
          )}

          {/* Opening Hours */}
          {hours && (
            <section className="lg:col-span-2 bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">Opening Hours</h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.hours)}
                </span>
              </div>

              <div className="flex flex-col gap-2.5">
                {WEEK_DAYS.map((day: WeekDay) => {
                  const dayHours = hours.days[day];
                  const isOpen = dayHours !== null;
                  return (
                    <div
                      key={day}
                      className="flex flex-wrap items-center justify-between gap-3 p-3.5 bg-surface-2 border border-hair rounded-2xl"
                    >
                      <div className="flex items-center gap-3 min-w-[140px]">
                        <input
                          type="checkbox"
                          id={`day-open-${day}`}
                          checked={isOpen}
                          onChange={(e) => {
                            const nextOpen = e.target.checked;
                            setContent((prev) => ({
                              ...prev,
                              hours: {
                                ...hours,
                                days: {
                                  ...hours.days,
                                  [day]: nextOpen
                                    ? { opens: '11:00', closes: '21:00' }
                                    : null,
                                },
                              },
                            }));
                          }}
                          className="w-4 h-4 accent-emerald-500 rounded"
                        />
                        <label
                          htmlFor={`day-open-${day}`}
                          className="text-xs font-bold text-txt-1 cursor-pointer"
                        >
                          {WEEK_DAY_LABELS[day]}
                        </label>
                      </div>

                      {isOpen ? (
                        <div className="flex items-center gap-2">
                          <label className="flex items-center gap-1.5 text-xs text-txt-2">
                            <span>Opens</span>
                            <input
                              type="time"
                              aria-label={`${WEEK_DAY_LABELS[day]} opens`}
                              value={dayHours.opens}
                              onChange={(e) =>
                                setContent((prev) => ({
                                  ...prev,
                                  hours: {
                                    ...hours,
                                    days: {
                                      ...hours.days,
                                      [day]: {
                                        ...dayHours,
                                        opens: e.target.value,
                                      },
                                    },
                                  },
                                }))
                              }
                              className="bg-surface border border-hair rounded-xl px-2.5 py-1.5 min-h-[40px] text-xs font-semibold text-txt-1"
                            />
                          </label>
                          <span className="text-txt-3">to</span>
                          <label className="flex items-center gap-1.5 text-xs text-txt-2">
                            <span>Closes</span>
                            <input
                              type="time"
                              aria-label={`${WEEK_DAY_LABELS[day]} closes`}
                              value={dayHours.closes}
                              onChange={(e) =>
                                setContent((prev) => ({
                                  ...prev,
                                  hours: {
                                    ...hours,
                                    days: {
                                      ...hours.days,
                                      [day]: {
                                        ...dayHours,
                                        closes: e.target.value,
                                      },
                                    },
                                  },
                                }))
                              }
                              className="bg-surface border border-hair rounded-xl px-2.5 py-1.5 min-h-[40px] text-xs font-semibold text-txt-1"
                            />
                          </label>
                        </div>
                      ) : (
                        <span className="text-xs font-bold text-txt-3 px-2.5 py-1 rounded-full bg-surface">
                          Closed
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>

              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-txt-2">
                  Schedule note (shown below the hours)
                </span>
                <input
                  type="text"
                  maxLength={300}
                  value={hours.note}
                  onChange={(e) =>
                    setContent((prev) => ({
                      ...prev,
                      hours: { ...hours, note: e.target.value },
                    }))
                  }
                  className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                />
              </label>

              {renderFeedback('hours')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('hours')}
                  disabled={savingSection === 'hours'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'hours' ? 'Saving…' : 'Save opening hours'}
                </button>
              </div>
            </section>
          )}
        </div>
      )}

      {/* GROUP 2: CONTACT, TAKEOUT FEE, ADDRESS & PAYMENTS */}
      {activeGroup === 'contact' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Contact Details */}
          {contact && (
            <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Phone className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">Contact Details</h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.contact)}
                </span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <label className="flex flex-col gap-1.5">
                  <span className="text-xs font-bold text-txt-2">Phone number</span>
                  <input
                    type="text"
                    maxLength={20}
                    value={contact.phone}
                    onChange={(e) =>
                      setContent((prev) => ({
                        ...prev,
                        contact: { ...contact, phone: e.target.value },
                      }))
                    }
                    className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                  />
                </label>
                <label className="flex flex-col gap-1.5">
                  <span className="text-xs font-bold text-txt-2">Contact email</span>
                  <input
                    type="email"
                    maxLength={254}
                    value={contact.email}
                    onChange={(e) =>
                      setContent((prev) => ({
                        ...prev,
                        contact: { ...contact, email: e.target.value },
                      }))
                    }
                    className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                  />
                </label>
              </div>
              {renderFeedback('contact')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('contact')}
                  disabled={savingSection === 'contact'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'contact' ? 'Saving…' : 'Save contact details'}
                </button>
              </div>
            </section>
          )}

          {/* Take-out Box Fee */}
          <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Package className="w-4 h-4 text-emerald-400" />
                <h4 className="text-sm font-black text-txt-1">Take-out Box Fee</h4>
              </div>
              <span className="text-[11px] text-txt-3 font-medium">
                Synced with Menu Catalog
              </span>
            </div>
            <p className="text-xs text-txt-3 leading-relaxed">
              The website reads the take-out box fee directly from the{' '}
              <strong className="text-txt-2">Take-out Box</strong> item in the shared
              menu catalog so POS and website prices never drift.
            </p>
            <label className="flex flex-col gap-1.5 max-w-xs">
              <span className="text-xs font-bold text-txt-2">
                Take-out box fee (₱)
              </span>
              <input
                type="number"
                min={1}
                step={1}
                value={takeoutFeeInput}
                onChange={(e) => setTakeoutFeeInput(e.target.value)}
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50 font-semibold"
              />
            </label>
            {renderFeedback('takeout')}
            <div className="flex justify-end">
              <button
                onClick={saveTakeoutBoxFee}
                disabled={savingSection === 'takeout' || !takeoutItem}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
              >
                <Save className="w-3.5 h-3.5" />
                {savingSection === 'takeout' ? 'Saving…' : 'Save take-out fee'}
              </button>
            </div>
          </section>

          {/* Address & Map */}
          {address && (
            <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">Shop Address &amp; Map</h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.address)}
                </span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {(
                  [
                    { key: 'street', label: 'Street' },
                    { key: 'area', label: 'Barangay / Area' },
                    { key: 'town', label: 'Town / Municipality' },
                    { key: 'province', label: 'Province' },
                    { key: 'postalCode', label: 'Postal code (4 digits)' },
                    { key: 'country', label: 'Country' },
                  ] as const
                ).map(({ key, label }) => (
                  <label key={key} className="flex flex-col gap-1.5">
                    <span className="text-xs font-bold text-txt-2">{label}</span>
                    <input
                      type="text"
                      value={address[key]}
                      onChange={(e) =>
                        setContent((prev) => ({
                          ...prev,
                          address: { ...address, [key]: e.target.value },
                        }))
                      }
                      className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                    />
                  </label>
                ))}
              </div>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-txt-2">Landmark</span>
                <input
                  type="text"
                  maxLength={160}
                  value={address.landmark}
                  onChange={(e) =>
                    setContent((prev) => ({
                      ...prev,
                      address: { ...address, landmark: e.target.value },
                    }))
                  }
                  className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-bold text-txt-2">
                  Google Maps URL (https://)
                </span>
                <input
                  type="url"
                  maxLength={500}
                  value={address.mapUrl}
                  onChange={(e) =>
                    setContent((prev) => ({
                      ...prev,
                      address: { ...address, mapUrl: e.target.value },
                    }))
                  }
                  className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                />
              </label>
              {renderFeedback('address')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('address')}
                  disabled={savingSection === 'address'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'address' ? 'Saving…' : 'Save address'}
                </button>
              </div>
            </section>
          )}

          {/* Payment Methods & QR */}
          {payments && (
            <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <CreditCard className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">
                    Accepted Payments &amp; QR
                  </h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.payments)}
                </span>
              </div>

              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-txt-2">
                    Payment methods (1 to 6)
                  </span>
                  {payments.methods.length < 6 && (
                    <button
                      type="button"
                      onClick={() =>
                        setContent((prev) => ({
                          ...prev,
                          payments: {
                            ...payments,
                            methods: [...payments.methods, ''],
                          },
                        }))
                      }
                      className="inline-flex items-center gap-1 px-3 py-1.5 min-h-[36px] bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border border-emerald-500/25 rounded-full text-xs font-bold"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add method
                    </button>
                  )}
                </div>
                {payments.methods.map((method, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <input
                      type="text"
                      maxLength={30}
                      aria-label={`Payment method ${idx + 1}`}
                      value={method}
                      onChange={(e) => {
                        const next = [...payments.methods];
                        next[idx] = e.target.value;
                        setContent((prev) => ({
                          ...prev,
                          payments: { ...payments, methods: next },
                        }));
                      }}
                      className="flex-1 bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                    />
                    {payments.methods.length > 1 && (
                      <button
                        type="button"
                        aria-label={`Remove payment method ${idx + 1}`}
                        onClick={() =>
                          setContent((prev) => ({
                            ...prev,
                            payments: {
                              ...payments,
                              methods: payments.methods.filter((_, i) => i !== idx),
                            },
                          }))
                        }
                        className="p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center rounded-2xl bg-red-500/10 text-red-400 hover:bg-red-500/20"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                ))}
              </div>

              <div className="flex flex-col gap-2 border-t border-hair pt-3">
                <label className="flex items-center gap-2 text-xs font-bold text-txt-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={payments.qrCode !== null}
                    onChange={(e) =>
                      setContent((prev) => ({
                        ...prev,
                        payments: {
                          ...payments,
                          qrCode: e.target.checked
                            ? {
                                src: '/images/payment/gcash-qr.svg',
                                alt: 'Brew ni Cat GCash and InstaPay QR code',
                              }
                            : null,
                        },
                      }))
                    }
                    className="w-4 h-4 accent-emerald-500 rounded"
                  />
                  Show GCash QR code on pickup checkout
                </label>
                {payments.qrCode && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-1">
                    <label className="flex flex-col gap-1">
                      <span className="text-[11px] font-bold text-txt-3">
                        QR image path (/images/…)
                      </span>
                      <input
                        type="text"
                        value={payments.qrCode.src}
                        onChange={(e) =>
                          setContent((prev) => ({
                            ...prev,
                            payments: {
                              ...payments,
                              qrCode: {
                                ...payments.qrCode!,
                                src: e.target.value,
                              },
                            },
                          }))
                        }
                        className="bg-surface-2 border border-hair rounded-2xl px-3 py-2 min-h-[40px] text-xs text-txt-1"
                      />
                    </label>
                    <label className="flex flex-col gap-1">
                      <span className="text-[11px] font-bold text-txt-3">
                        QR alt text
                      </span>
                      <input
                        type="text"
                        value={payments.qrCode.alt}
                        onChange={(e) =>
                          setContent((prev) => ({
                            ...prev,
                            payments: {
                              ...payments,
                              qrCode: {
                                ...payments.qrCode!,
                                alt: e.target.value,
                              },
                            },
                          }))
                        }
                        className="bg-surface-2 border border-hair rounded-2xl px-3 py-2 min-h-[40px] text-xs text-txt-1"
                      />
                    </label>
                  </div>
                )}
              </div>

              {renderFeedback('payments')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('payments')}
                  disabled={savingSection === 'payments'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'payments' ? 'Saving…' : 'Save payments'}
                </button>
              </div>
            </section>
          )}
        </div>
      )}

      {/* GROUP 3: SOCIAL LINKS & DELIVERY PARTNERS */}
      {activeGroup === 'channels' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {social && (
            <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4 h-fit">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Share2 className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">Social Links</h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.social)}
                </span>
              </div>
              {(
                [
                  { key: 'facebook', label: 'Facebook page URL' },
                  { key: 'facebookPhotos', label: 'Facebook photos URL' },
                  { key: 'tiktok', label: 'TikTok URL' },
                ] as const
              ).map(({ key, label }) => (
                <label key={key} className="flex flex-col gap-1.5">
                  <span className="text-xs font-bold text-txt-2">
                    {label} (leave empty to hide)
                  </span>
                  <input
                    type="url"
                    maxLength={500}
                    value={social[key]}
                    onChange={(e) =>
                      setContent((prev) => ({
                        ...prev,
                        social: { ...social, [key]: e.target.value },
                      }))
                    }
                    className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1 outline-none focus:border-emerald-500/50"
                  />
                </label>
              ))}
              {renderFeedback('social')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('social')}
                  disabled={savingSection === 'social'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'social' ? 'Saving…' : 'Save social links'}
                </button>
              </div>
            </section>
          )}

          {riders && (
            <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Truck className="w-4 h-4 text-emerald-400" />
                  <h4 className="text-sm font-black text-txt-1">
                    Independent Delivery Partners
                  </h4>
                </div>
                <span className="text-[11px] text-txt-3 font-medium">
                  {formatTimestamp(updatedAt.riders)}
                </span>
              </div>

              <div className="flex flex-col gap-3">
                {riders.partners.map((partner, idx) => (
                  <div
                    key={idx}
                    className="p-3.5 bg-surface-2 border border-hair rounded-2xl flex flex-col gap-2.5"
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-txt-2">
                        Partner #{idx + 1}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          setContent((prev) => ({
                            ...prev,
                            riders: {
                              partners: riders.partners.filter((_, i) => i !== idx),
                            },
                          }))
                        }
                        className="p-1.5 rounded-xl bg-red-500/10 text-red-400 hover:bg-red-500/20"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                    <input
                      type="text"
                      placeholder="Partner name"
                      value={partner.name}
                      onChange={(e) => {
                        const next = [...riders.partners];
                        next[idx] = { ...partner, name: e.target.value };
                        setContent((prev) => ({
                          ...prev,
                          riders: { partners: next },
                        }));
                      }}
                      className="bg-surface border border-hair rounded-xl px-3 py-2 min-h-[40px] text-xs text-txt-1"
                    />
                    <input
                      type="url"
                      placeholder="https://facebook.com/..."
                      value={partner.href}
                      onChange={(e) => {
                        const next = [...riders.partners];
                        next[idx] = { ...partner, href: e.target.value };
                        setContent((prev) => ({
                          ...prev,
                          riders: { partners: next },
                        }));
                      }}
                      className="bg-surface border border-hair rounded-xl px-3 py-2 min-h-[40px] text-xs text-txt-1"
                    />
                    <input
                      type="text"
                      placeholder="/images/riders/..."
                      value={partner.logo}
                      onChange={(e) => {
                        const next = [...riders.partners];
                        next[idx] = { ...partner, logo: e.target.value };
                        setContent((prev) => ({
                          ...prev,
                          riders: { partners: next },
                        }));
                      }}
                      className="bg-surface border border-hair rounded-xl px-3 py-2 min-h-[40px] text-xs text-txt-1"
                    />
                  </div>
                ))}
                {riders.partners.length < 8 && (
                  <button
                    type="button"
                    onClick={() =>
                      setContent((prev) => ({
                        ...prev,
                        riders: {
                          partners: [
                            ...riders.partners,
                            {
                              name: '',
                              href: 'https://www.facebook.com/',
                              logo: '/images/riders/papas-delivery.jpg',
                            },
                          ],
                        },
                      }))
                    }
                    className="inline-flex items-center justify-center gap-1.5 px-4 py-2.5 min-h-[44px] bg-surface-2 hover:bg-surface-3 border border-hair rounded-2xl text-xs font-bold text-emerald-400"
                  >
                    <Plus className="w-4 h-4" /> Add delivery partner
                  </button>
                )}
              </div>

              {renderFeedback('riders')}
              <div className="flex justify-end">
                <button
                  onClick={() => saveSection('riders')}
                  disabled={savingSection === 'riders'}
                  className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
                >
                  <Save className="w-3.5 h-3.5" />
                  {savingSection === 'riders' ? 'Saving…' : 'Save delivery partners'}
                </button>
              </div>
            </section>
          )}
        </div>
      )}

      {/* GROUP 4: ABOUT & HOME COPY */}
      {activeGroup === 'about' && about && (
        <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-5">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <BookOpen className="w-4 h-4 text-emerald-400" />
              <h4 className="text-sm font-black text-txt-1">
                About Page &amp; Home Copy
              </h4>
            </div>
            <span className="text-[11px] text-txt-3 font-medium">
              {formatTimestamp(updatedAt.about)}
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Shop opening date (YYYY-MM-DD)
              </span>
              <input
                type="date"
                value={about.openedOn}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: { ...about, openedOn: e.target.value },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Menu summary line (≤80 chars)
              </span>
              <input
                type="text"
                maxLength={80}
                value={about.menuSummary}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: { ...about, menuSummary: e.target.value },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Hero title (≤80 chars)
              </span>
              <input
                type="text"
                maxLength={80}
                value={about.heroTitle}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: { ...about, heroTitle: e.target.value },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Home section title (≤80 chars)
              </span>
              <input
                type="text"
                maxLength={80}
                value={about.homeTitle}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: { ...about, homeTitle: e.target.value },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1"
              />
            </label>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Hero text (≤300 chars)
              </span>
              <textarea
                rows={2}
                maxLength={300}
                value={about.heroText}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: {
                      ...about,
                      heroText: e.target.value.replace(/[\r\n]+/g, ' '),
                    },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Home section text (≤300 chars)
              </span>
              <textarea
                rows={2}
                maxLength={300}
                value={about.homeText}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: {
                      ...about,
                      homeText: e.target.value.replace(/[\r\n]+/g, ' '),
                    },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                About intro (≤300 chars)
              </span>
              <textarea
                rows={2}
                maxLength={300}
                value={about.intro}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: {
                      ...about,
                      intro: e.target.value.replace(/[\r\n]+/g, ' '),
                    },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Footer description (≤300 chars)
              </span>
              <textarea
                rows={2}
                maxLength={300}
                value={about.footerText}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: {
                      ...about,
                      footerText: e.target.value.replace(/[\r\n]+/g, ' '),
                    },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1"
              />
            </label>
          </div>

          <div className="flex flex-col gap-2 border-t border-hair pt-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-txt-2">
                Story heading (≤80 chars)
              </span>
              <input
                type="text"
                maxLength={80}
                value={about.storyTitle}
                onChange={(e) =>
                  setContent((prev) => ({
                    ...prev,
                    about: { ...about, storyTitle: e.target.value },
                  }))
                }
                className="bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 min-h-[44px] text-xs text-txt-1"
              />
            </label>
            <span className="text-xs font-bold text-txt-2 mt-2">
              Story paragraphs (1 to 6)
            </span>
            {about.story.map((paragraph, idx) => (
              <div key={idx} className="flex items-start gap-2">
                <textarea
                  rows={2}
                  maxLength={1000}
                  value={paragraph}
                  onChange={(e) => {
                    const next = [...about.story];
                    next[idx] = e.target.value.replace(/[\r\n]+/g, ' ');
                    setContent((prev) => ({
                      ...prev,
                      about: { ...about, story: next },
                    }));
                  }}
                  className="flex-1 bg-surface-2 border border-hair rounded-2xl px-3.5 py-2.5 text-xs text-txt-1"
                />
                {about.story.length > 1 && (
                  <button
                    type="button"
                    onClick={() =>
                      setContent((prev) => ({
                        ...prev,
                        about: {
                          ...about,
                          story: about.story.filter((_, i) => i !== idx),
                        },
                      }))
                    }
                    className="p-2.5 min-h-[44px] min-w-[44px] flex items-center justify-center rounded-2xl bg-red-500/10 text-red-400"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-2 border-t border-hair pt-4">
            <span className="text-xs font-bold text-txt-2">
              Cat house rules (up to 6)
            </span>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {about.catRules.map((rule, idx) => (
                <div
                  key={idx}
                  className="p-3.5 bg-surface-2 border border-hair rounded-2xl flex flex-col gap-2"
                >
                  <input
                    type="text"
                    maxLength={80}
                    value={rule.title}
                    onChange={(e) => {
                      const next = [...about.catRules];
                      next[idx] = { ...rule, title: e.target.value };
                      setContent((prev) => ({
                        ...prev,
                        about: { ...about, catRules: next },
                      }));
                    }}
                    className="bg-surface border border-hair rounded-xl px-3 py-2 text-xs font-bold text-txt-1"
                  />
                  <textarea
                    rows={2}
                    maxLength={300}
                    value={rule.text}
                    onChange={(e) => {
                      const next = [...about.catRules];
                      next[idx] = {
                        ...rule,
                        text: e.target.value.replace(/[\r\n]+/g, ' '),
                      };
                      setContent((prev) => ({
                        ...prev,
                        about: { ...about, catRules: next },
                      }));
                    }}
                    className="bg-surface border border-hair rounded-xl px-3 py-2 text-xs text-txt-2"
                  />
                </div>
              ))}
            </div>
          </div>

          {renderFeedback('about')}
          <div className="flex justify-end">
            <button
              onClick={() => saveSection('about')}
              disabled={savingSection === 'about'}
              className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
            >
              <Save className="w-3.5 h-3.5" />
              {savingSection === 'about' ? 'Saving…' : 'Save About & Home copy'}
            </button>
          </div>
        </section>
      )}

      {/* GROUP 5: GALLERY PHOTOS */}
      {activeGroup === 'gallery' && gallery && (
        <section className="bg-surface border border-hair rounded-3xl p-6 flex flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="flex items-center gap-2">
                <ImageIcon className="w-4 h-4 text-emerald-400" />
                <h4 className="text-sm font-black text-txt-1">
                  Curated Gallery Photos ({gallery.photos.length} / 80)
                </h4>
              </div>
              <p className="text-xs text-txt-3 mt-0.5">
                Photos must reference approved files under <code>/images/</code>.
                Keep count divisible by 6 for a flush 2- and 3-column grid.
              </p>
            </div>
            <div className="flex items-center gap-2">
              {gallery.photos.length < 80 && (
                <button
                  type="button"
                  onClick={() =>
                    setContent((prev) => ({
                      ...prev,
                      gallery: {
                        photos: [
                          {
                            src: '/images/shop/photo_148.jpg',
                            alt: 'Brew ni Cat café moment',
                          },
                          ...gallery.photos,
                        ],
                      },
                    }))
                  }
                  className="inline-flex items-center gap-1.5 px-4 py-2.5 min-h-[44px] bg-surface-2 hover:bg-surface-3 border border-hair rounded-full text-xs font-bold text-emerald-400"
                >
                  <Plus className="w-3.5 h-3.5" /> Add photo
                </button>
              )}
              <button
                onClick={() => saveSection('gallery')}
                disabled={savingSection === 'gallery'}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 min-h-[44px] bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 rounded-full text-xs font-black transition-colors"
              >
                <Save className="w-3.5 h-3.5" />
                {savingSection === 'gallery' ? 'Saving…' : 'Save gallery'}
              </button>
            </div>
          </div>

          {renderFeedback('gallery')}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-[520px] overflow-y-auto pr-1">
            {gallery.photos.map((photo, idx) => (
              <div
                key={idx}
                className="p-3.5 bg-surface-2 border border-hair rounded-2xl flex flex-col gap-2"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-bold text-txt-3">
                    #{idx + 1}
                  </span>
                  <div className="flex items-center gap-3 text-[11px] text-txt-2">
                    <label className="flex items-center gap-1 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={Boolean(photo.featured)}
                        onChange={(e) => {
                          const next = [...gallery.photos];
                          const updated = { ...photo };
                          if (e.target.checked) updated.featured = true;
                          else delete updated.featured;
                          next[idx] = updated;
                          setContent((prev) => ({
                            ...prev,
                            gallery: { photos: next },
                          }));
                        }}
                        className="accent-emerald-500"
                      />
                      Featured
                    </label>
                    <label className="flex items-center gap-1 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={Boolean(photo.people)}
                        onChange={(e) => {
                          const next = [...gallery.photos];
                          const updated = { ...photo };
                          if (e.target.checked) updated.people = true;
                          else delete updated.people;
                          next[idx] = updated;
                          setContent((prev) => ({
                            ...prev,
                            gallery: { photos: next },
                          }));
                        }}
                        className="accent-emerald-500"
                      />
                      People
                    </label>
                    {gallery.photos.length > 1 && (
                      <button
                        type="button"
                        onClick={() =>
                          setContent((prev) => ({
                            ...prev,
                            gallery: {
                              photos: gallery.photos.filter((_, i) => i !== idx),
                            },
                          }))
                        }
                        className="p-1 rounded-lg text-red-400 hover:bg-red-500/10"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                </div>
                <input
                  type="text"
                  value={photo.src}
                  onChange={(e) => {
                    const next = [...gallery.photos];
                    next[idx] = { ...photo, src: e.target.value };
                    setContent((prev) => ({
                      ...prev,
                      gallery: { photos: next },
                    }));
                  }}
                  className="bg-surface border border-hair rounded-xl px-3 py-1.5 text-xs text-txt-1"
                />
                <input
                  type="text"
                  value={photo.alt}
                  onChange={(e) => {
                    const next = [...gallery.photos];
                    next[idx] = { ...photo, alt: e.target.value };
                    setContent((prev) => ({
                      ...prev,
                      gallery: { photos: next },
                    }));
                  }}
                  className="bg-surface border border-hair rounded-xl px-3 py-1.5 text-xs text-txt-2"
                />
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
