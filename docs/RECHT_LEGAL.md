<a id="english"></a>

# Road traffic, law and liability

🇬🇧 **English** · 🇩🇪 [Deutsch (German, below)](#deutsch)

> [!CAUTION]
> **AmbientFX is meant for testing on private property only. It may not be permitted in public road traffic and should not be used there.**

**Not legal advice.** This document is a cautious assessment by the author (not a lawyer), based on the statute texts retrieved on 2026-11-05 from [gesetze-im-internet.de](https://www.gesetze-im-internet.de). Laws and their interpretation can change. If in doubt, ask an inspection body (TÜV/DEKRA), your registration authority or a lawyer. The references are to **German** law; other countries differ.

## Summary

- AmbientFX is **not** approved by Audi/VW and has **no** operating licence, **no** type approval and **no** approval mark.
- The provisions checked do **not explicitly mention** interior/ambient lighting, so there is **no explicit permission**. Whether a particular use is permitted depends on the individual case.
- The effects do things that **can be critical**: light that changes with **speed, throttle and brake**, **red light when braking**, brightness changes and flash effects (white on D → S).
- **Therefore: for testing on private property only, not on public roads.** Anyone using it in road traffic anyway does so on their own responsibility and risk.

## What was checked

| Provision | Content (paraphrased) | Relevance to AmbientFX |
|---|---|---|
| **StVO § 23 (1)** | Vision and hearing must not be impaired by devices or other circumstances. | Glare, reflections in windows and mirrors, especially at night and with bright effects (white, fast changes). |
| **StVO § 23 (1a)** | Regulates the use of electronic devices (looking at, touching). | Concerns operating the menu while driving: do not adjust while driving. |
| **StVO § 1** | Constant caution and mutual consideration; nobody may be endangered, obstructed or annoyed more than unavoidable. | General clause: distraction of the driver and irritation of others. |
| **StVZO § 19 (2)** | The operating licence expires for modifications likely to endanger other road users. After it expires the vehicle must not be used on public roads ((5)). Special notes apply to software changes ((2) sentence 5). | Whether a pure software change of the ambient lighting falls under this is open and a matter of the individual case. |
| **StVZO § 22a, § 49a** | Lighting devices need a type approval or may only be fitted in permitted form. § 49a also counts “externally effective systems for variable or dynamic optical display”. Red light and confusion with brake lights are specially regulated. | The key point is “externally effective”: light from the interior acting outward through the windows, especially **red** and **dynamic** light. Whether and when that applies is not clearly settled. |
| **StVZO § 30** | Vehicles must be built so that ordinary operation does not endanger, obstruct or annoy anyone more than unavoidable. | like StVO § 1 |
| **StVG § 24, StVZO § 69a** | Violations can be administrative offences (fines). | Consequences depend on the specific violation. |

**Not checked** (and relevant for an assessment): StVZO §§ 53, 54a, 67, the fine catalogue, EU/UNECE rules, other countries.

## Possible practical consequences

- **Loss of the operating licence** and trouble at the periodic inspection or in a road check.
- **Fines**, and in a dispute problems with **warranty** and **insurance cover**. How a workshop or insurer decides, I cannot judge.
- **Distraction** and **glare** are real safety risks regardless of the legal position.

## What AmbientFX does technically – and what it does not

- It acts **only on the contour of the interior ambient lighting**. It does not control exterior lighting or brake lights and does not interfere with brakes, steering or engine.
- The code contains **safety clamps** (for example a limited hue range in Drive, dimmed night values). They do not replace legal admissibility.
- Red light when braking (Momentum) is a **design decision**, not a brake light.

## Recommendation

Test **on private property only** (standing or at walking speed), **do not operate the menu while driving**, and **do not** use the effects in public road traffic. If you handle it differently, check the legal position beforehand yourself and bear the responsibility.

## Liability

Software and documentation are provided “as is” without any warranty (see [LICENSE.md](../LICENSE.md)). The author is not liable for damage, fines, lost warranty, data loss or malfunctions of the head unit. **Make a backup before any intervention** and know how to get back (see [INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)).

---

<a id="deutsch"></a>

# Straßenverkehr, Recht und Haftung

🇩🇪 **Deutsch** · 🇬🇧 [English (oben)](#english)

> [!CAUTION]
> **AmbientFX ist nur für Tests auf privatem Grundstück gedacht. Im öffentlichen Straßenverkehr ist es womöglich nicht zulässig und sollte dort nicht genutzt werden.**

**Kein Rechtsrat.** Dieses Dokument ist eine vorsichtige Einordnung des Autors (kein Jurist) auf Basis der Gesetzestexte, abgerufen am 05.11.2026 von [gesetze-im-internet.de](https://www.gesetze-im-internet.de). Gesetze und Auslegung können sich ändern. Im Zweifel frag eine Prüfstelle (TÜV/DEKRA), deine Zulassungsstelle oder einen Anwalt.

## Kurzfassung

- AmbientFX ist **nicht** von Audi/VW freigegeben, hat **keine** Betriebserlaubnis, **keine** Bauartgenehmigung und **kein** Prüfzeichen.
- In den geprüften Vorschriften wird Innenraum-/Ambientebeleuchtung **nicht ausdrücklich** erwähnt, es gibt also **keine ausdrückliche Erlaubnis**. Ob eine bestimmte Nutzung zulässig ist, hängt vom Einzelfall ab.
- Die Effekte machen Dinge, die **kritisch** sein können: Licht, das sich mit **Tempo, Gas und Bremse** ändert, **rotes Licht beim Bremsen**, Helligkeitsänderungen und Blitz-Effekte (Weiß beim Schalten D → S).
- **Deshalb: nur zu Testzwecken auf privatem Grundstück, nicht auf öffentlichen Straßen.** Wer es trotzdem im Straßenverkehr nutzt, tut das auf eigene Verantwortung und eigenes Risiko.

## Was geprüft wurde

| Vorschrift | Inhalt (sinngemäß) | Bezug zu AmbientFX |
|---|---|---|
| **StVO § 23 Abs. 1** | Sicht und Gehör dürfen durch Geräte oder andere Umstände nicht beeinträchtigt werden. | Blendung, Spiegelungen in Scheibe und Spiegeln, besonders nachts und bei hellen Effekten (Weiß, schnelle Wechsel). |
| **StVO § 23 Abs. 1a** | Regelt die Bedienung elektronischer Geräte (Blickzuwendung, Berühren). | Betrifft das Bedienen des Menüs während der Fahrt: nicht während der Fahrt einstellen. |
| **StVO § 1** | Ständige Vorsicht und gegenseitige Rücksicht, niemand darf mehr als unvermeidbar gefährdet, behindert oder belästigt werden. | Generalklausel: Ablenkung des Fahrers und Irritation anderer. |
| **StVZO § 19 Abs. 2** | Die Betriebserlaubnis erlischt bei Änderungen, die eine Gefährdung anderer Verkehrsteilnehmer erwarten lassen. Nach Erlöschen darf das Fahrzeug nicht auf öffentlichen Straßen betrieben werden (Abs. 5). Für Softwareänderungen gelten besondere Hinweise (Abs. 2 Satz 5). | Ob eine reine Software-Änderung der Ambientebeleuchtung darunter fällt, ist offen und eine Frage des Einzelfalls. |
| **StVZO § 22a, § 49a** | Lichttechnische Einrichtungen brauchen eine Bauartgenehmigung beziehungsweise dürfen nur in zulässiger Ausführung angebracht sein. § 49a zählt auch „außenwirksame Anlagen zur variablen oder dynamischen optischen Anzeige“ dazu. Rotes Licht und Verwechslungsgefahr mit Bremsleuchten sind besonders geregelt. | Entscheidend ist „außenwirksam“: Licht aus dem Innenraum, das durch die Scheiben nach außen wirkt, besonders **rotes** und **dynamisches** Licht. Ob und wann das darunter fällt, ist nicht eindeutig geklärt. |
| **StVZO § 30** | Fahrzeuge müssen so beschaffen sein, dass ihr verkehrsüblicher Betrieb niemanden mehr als unvermeidbar gefährdet, behindert oder belästigt. | wie StVO § 1 |
| **StVG § 24, StVZO § 69a** | Verstöße können Ordnungswidrigkeiten sein (Bußgeld). | Folgen hängen vom konkreten Verstoß ab. |

**Nicht geprüft** (und für eine Einschätzung relevant): StVZO §§ 53, 54a, 67, Bußgeldkatalog, EU-/UNECE-Regelungen, Besonderheiten anderer Länder.

## Praktische Folgen, die möglich sind

- **Erlöschen der Betriebserlaubnis** und Ärger bei Hauptuntersuchung oder Kontrolle.
- **Bußgeld**, im Streitfall auch Probleme mit **Garantie** und **Versicherungsschutz**. Wie die jeweilige Werkstatt oder Versicherung entscheidet, kann ich nicht beurteilen.
- **Ablenkung** und **Blendung** sind echte Sicherheitsrisiken, unabhängig von der Rechtslage.

## Was AmbientFX technisch dafür tut – und was nicht

- Es wirkt **nur auf die Kontur der Ambientebeleuchtung im Innenraum**. Es steuert keine Außenbeleuchtung und keine Bremslichter und greift nicht in Bremse, Lenkung oder Motor ein.
- Im Code gibt es **Sicherheitsklammern** (zum Beispiel begrenzter Farbbereich bei Drive, gedimmte Nachtwerte). Sie ersetzen keine rechtliche Zulässigkeit.
- Rotes Licht bei Bremsen (Momentum) ist eine **Gestaltungsentscheidung**, keine Bremsleuchte.

## Empfehlung

Teste nur **auf privatem Grundstück** (stehend oder bei Schrittgeschwindigkeit), **schalte das Menü nicht während der Fahrt**, und nutze die Effekte **nicht** im öffentlichen Straßenverkehr. Wenn du das anders handhabst, prüfe die Rechtslage vorher selbst und trage die Verantwortung.

## Haftung

Software und Dokumentation werden „wie besehen“ ohne jede Gewährleistung bereitgestellt (siehe [LICENSE.md](../LICENSE.md)). Der Autor haftet nicht für Schäden, Bußgelder, verlorene Garantie, Datenverlust oder Fehlfunktionen des Steuergeräts. **Mach vor jedem Eingriff ein Backup** und wisse, wie du zurückkommst (siehe [INSTALLATION_INSTALL.md](INSTALLATION_INSTALL.md)).
