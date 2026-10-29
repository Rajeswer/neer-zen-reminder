NEER ZEN — Master Design System Rules

Version 1.0 · Japanese Watercolor × Zen Minimalism · Android + Wear OS

1. Design philosophy

Core principles

1. Quiet, not empty. Every screen should feel considered, calm and purposeful.
2. Natural, not decorative. Watercolor artwork communicates the experience; it must not compete with content or controls.
3. Stone, mist and water. The interface feels grounded in nature through mineral greys, charcoal ink, muted sage and pale water blue.
4. Editorial, not generic. Typography and spacing create a premium reading experience.
5. Functional minimalism. Remove elements that do not help users understand, decide or act.
6. One system, two contexts. Android and Wear OS share the same visual identity but have different layouts, density and interaction rules.
7. Subtle motion, purposeful feedback. Animation should reinforce an action or natural metaphor—not constantly attract attention.

The experience should feel

- Meditative, grounded and quietly premium.
- Handcrafted without looking rustic.
- Japanese-inspired without relying on clichés.
- Minimal without feeling sterile.
- Organic without sacrificing precision.
- Consistent across every screen and device.

Explicitly prohibited

- Terracotta, orange and reddish-brown accent colours.
- Bright white backgrounds or cream-heavy surfaces.
- Generic purple AI gradients.
- Excessive sage-green surfaces.
- Gradients used as a substitute for a considered colour system.
- Glassmorphism, neon glows and excessive shadows.
- Random decorative circles, leaves or wave shapes.
- Emoji used as primary interface icons.
- Different visual styles between screens.
- Unnecessary dashboards, counters, badges and gamification.
- Shrinking the Android interface to create the Wear OS version.


---

2. Colour system

The interface must use semantic design tokens rather than arbitrary colour values embedded in components.

The following palette is the proposed starting point. Validate it against the approved mockup and accessibility requirements before implementation.

2.1 Foundation palette

Token	Hex	Purpose

AshCanvas	#E8E9E6	Primary application background
StoneSurface	#F0F1EE	Cards and elevated content surfaces
StoneSecondary	#DADDD9	Secondary surfaces and containers
MineralGrey	#A5ACA9	Muted UI elements and supporting details
DeepStone	#6D7774	Secondary text and selected icons
CharcoalInk	#252D2B	Primary text, headings and important icons
SumiInk	#171E1C	High-emphasis ink artwork
MutedSage	#788B7D	Secondary accent and selected states
DeepSage	#53695C	Stronger sage accent where contrast permits
PaleWater	#C8DDE1	Pale water-blue surfaces and illustrations
RiverBlue	#779FA8	Water indicators and supporting accents
MistLine	#C8CDCA	Dividers, outlines and subtle separators
PureWhite	#FFFFFF	Restricted use for contrast-critical elements
PureBlack	#000000	Reserved for technical needs, not general styling


Important: White is permitted only where required by contrast or platform conventions. It must not become the dominant visual background.

2.2 Semantic colour tokens

Components must not reference palette colours directly. They should reference semantic tokens.

Semantic token	Intended use

background	Main application canvas
surface	Primary content cards
surfaceSecondary	Supporting panels
textPrimary	Main text and headings
textSecondary	Supporting labels and descriptions
textDisabled	Disabled controls
actionPrimary	Primary actions
actionPrimaryContent	Text and icons on primary actions
actionSecondary	Secondary actions
borderSubtle	Dividers and component outlines
focusIndicator	Keyboard and accessibility focus
waterIndicator	Hydration-related visuals
selectionIndicator	Selected navigation and controls
success	Confirmed successful actions
warning	Important cautionary states
error	Errors and failed operations


Colour usage rules

- Ash grey is the dominant background.
- Charcoal ink carries the majority of readable text.
- Sage is a supporting accent, not a background colour for every component.
- Pale water blue is reserved for water-related content and occasional supporting surfaces.
- Mineral grey creates structure without excessive contrast.
- Primary actions must be visually distinct from decorative elements.
- Status colours must remain understandable without relying on colour alone.
- Do not introduce a new colour for an individual screen without updating the shared system.
- Avoid low-contrast grey text merely to achieve a muted aesthetic.

Accessibility gate: Validate actual foreground/background combinations against WCAG contrast requirements. Normal text should generally achieve at least 4.5:1 contrast; large text and qualifying non-text UI elements have different thresholds.


---

3. Typography system

The visual language combines editorial serif typography with a highly legible sans-serif interface.

3.1 Font roles

Editorial serif: Source Serif 4

Use for:

- Screen titles.
- Important reflective statements.
- Section introductions.
- Selected quotations and mindful prompts.

UI sans-serif: Inter or the platform's appropriately configured sans-serif.

Use for:

- Buttons.
- Navigation.
- Labels.
- Body text.
- Form controls.
- Hydration amounts and supporting information.
- Wear OS controls and compact data.

Use Source Serif 4 and Inter consistently across the app, or select suitable bundled equivalents if licensing, package size or platform support requires a change.

3.2 Type scale

Token	Size	Weight	Use

displayLarge	36 sp	Serif, regular	Rare introductory moments
headlineLarge	30 sp	Serif, regular	Major screen headings
headlineMedium	26 sp	Serif, regular	Secondary editorial headings
headlineSmall	22 sp	Serif, regular	Card or section headings
titleLarge	20 sp	Sans-serif, medium	Primary UI section titles
titleMedium	16 sp	Sans-serif, medium	Component headings
bodyLarge	16 sp	Sans-serif, regular	Main body copy
bodyMedium	14 sp	Sans-serif, regular	Supporting copy
labelLarge	14 sp	Sans-serif, medium	Buttons and prominent labels
labelMedium	12 sp	Sans-serif, medium	Supporting labels
labelSmall	11 sp	Sans-serif, medium	Restricted metadata use


These are starting tokens, not permission to force every screen into the same scale.

Typography rules

- Editorial serif should establish personality, not dominate every component.
- Sans-serif should carry most functional information.
- Avoid all-caps navigation and long uppercase labels.
- Do not use thin fonts for important information.
- Keep body copy comfortably readable.
- Use line height appropriate to the font and content.
- Allow text to wrap naturally rather than truncating essential instructions.
- Respect Android font scaling and accessibility settings.
- On Wear OS, prioritise legibility over editorial styling.
- Avoid introducing a third font without a compelling system-level reason.


---

4. Spacing and layout

Use a consistent spacing scale across Compose components.

Token	Value

space2	2 dp
space4	4 dp
space8	8 dp
space12	12 dp
space16	16 dp
space20	20 dp
space24	24 dp
space32	32 dp
space40	40 dp
space48	48 dp
space64	64 dp


Layout rules

- Use 16–24 dp as the typical Android horizontal content inset, adjusted for the screen and component.
- Group related elements closely; separate unrelated groups more generously.
- Establish a clear hierarchy through spacing before adding borders or shadows.
- Keep cards aligned to a common content grid.
- Avoid arbitrary offsets and negative spacing hacks.
- Avoid fixed-height containers around text that can wrap.
- Use responsive layouts that adapt to screen width and font scale.
- Keep the main action visually easy to locate.
- Do not fill unused space with decoration merely to make a screen look complete.

Density rule

Whitespace is part of the design system. Do not compress the interface simply to fit more content above the fold.


---

5. Shape, borders and elevation

The interface should feel like carefully arranged paper, stone and water—not a generic collection of floating cards.

Component	Starting rule

Small controls	10–12 dp radius
Standard cards	16–20 dp radius
Featured content	20–24 dp radius
Pills	Fully rounded only when the component's purpose supports it
Dividers	1 dp, low-contrast
Card elevation	Flat or very subtle
Modal elevation	Use platform-appropriate elevation
Outlines	Use when needed for separation or accessibility


Shape rules

- Avoid excessive rounded rectangles.
- Do not place every element inside its own card.
- Use open layouts when hierarchy is already clear.
- Keep corner radii consistent across related components.
- Avoid heavy drop shadows and floating-card overload.
- Use tonal separation before elevation.
- Do not use decorative outlines around every illustration.
- Prefer one clear container over multiple nested containers.


---

6. Watercolor and illustration system

Illustration is the defining brand asset. It must look like a coherent collection—not unrelated images generated for individual screens.

6.1 Art direction

The style combines:

- Japanese sumi-e ink painting.
- Soft mineral-watercolor washes.
- Hand-painted Enso circles.
- Mist-covered mountain silhouettes.
- Sparse bamboo.
- Water ripples.
- Individual droplets.
- River stones.
- Natural paper and pigment textures.

The artwork should feel imperfect in a deliberate, handcrafted way.

6.2 Illustration rules

Enso circles

- Use visible brush texture and natural variation.
- Avoid perfectly geometric vector circles.
- Keep gaps, overlaps and uneven edges intentional.
- Use as a focal element or meaningful framing device.
- Do not place an Enso on every screen.

Mountains

- Use layered, low-contrast silhouettes.
- Suggest mist through soft edges and negative space.
- Keep the foreground quiet.
- Avoid highly detailed fantasy landscapes.

Bamboo

- Use sparse, directional brushwork.
- Avoid dense bamboo forests and repetitive stock illustrations.
- Place bamboo at the edges of a composition rather than behind important text.

Water

- Use restrained ripples, translucent washes and minimal linework.
- Avoid bright cyan, neon blue or glossy liquid effects.
- Prefer horizontal movement and gentle asymmetry.

Droplets

- Use simple painted forms.
- Avoid shiny 3D droplets and exaggerated reflections.
- Use them where they reinforce hydration or water-related content.

River stones

- Use irregular organic silhouettes.
- Keep shading soft and grounded.
- Avoid overly polished, glossy or photorealistic stones.

6.3 Illustration colour rules

Artwork may use a wider tonal range than the UI, but it must remain within the brand's visual language.

- Charcoal ink provides definition.
- Mineral grey creates atmospheric depth.
- Muted sage adds limited natural colour.
- Pale water blue communicates water and mist.
- The background should remain visible through the composition wherever appropriate.
- Avoid saturated colours and hard-edged vector aesthetics.
- Avoid large areas of dark pigment that overpower the UI.

6.4 Artwork hierarchy

Use three levels:

1. Hero artwork: One main painted composition that establishes the screen's mood.
2. Supporting artwork: Small illustrations that help explain a feature or guide an action.
3. Micro-motifs: Minimal droplets, ripples or brush marks used sparingly.

Never combine multiple competing hero illustrations on one screen.

6.5 Asset production rules

- Prefer original, approved artwork over arbitrary stock imagery.
- Use vector assets for icons and raster artwork for painterly textures where appropriate.
- Preserve transparent backgrounds when the artwork needs to blend with the ash canvas.
- Check image edges for unwanted white or cream rectangles.
- Avoid stretching, distorting or aggressively cropping painted compositions.
- Provide dark- and light-context variants only when the design requires them.
- Store artwork in a documented asset library.
- Name assets by purpose, not by temporary screen position.

Example naming convention:

illustration_enso_primary
illustration_mountain_mist
illustration_bamboo_edge
illustration_water_ripple
illustration_water_droplet
illustration_river_stones

Critical: Artwork must not become a substitute for functional UI. Hydration amounts, reminders, actions and instructions must remain readable without interpreting an illustration.


---

7. Iconography

Icons should be quiet, consistent and recognisable at a glance.

Rules

- Use one coherent icon family.
- Prefer simple outline or restrained solid icons.
- Keep stroke weight consistent.
- Avoid emoji and mixed icon styles.
- Avoid using illustrations as substitutes for standard interface icons.
- Use familiar symbols for settings, navigation, back, notifications and confirmation.
- Use custom symbols only when they provide genuine product meaning.
- Pair ambiguous icons with text.
- Ensure interactive icon buttons have accessible labels.

Icons should support the interface—not compete with the watercolor artwork.


---

8. Component system

Build a reusable component library rather than designing each screen independently.

8.1 Core components

ZenScaffold

Responsibilities:

- Apply the correct background.
- Respect system bars and insets.
- Establish the screen's content area.
- Support consistent screen-level layout.

ZenTopBar

Responsibilities:

- Display the screen title.
- Support navigation where needed.
- Use consistent typography and alignment.
- Avoid unnecessary actions and decoration.

ZenPrimaryButton

Responsibilities:

- Communicate the main action.
- Provide accessible touch targets.
- Support enabled, disabled, loading and pressed states.
- Prevent repeated submission where appropriate.

ZenSecondaryButton

Responsibilities:

- Support less prominent actions.
- Remain visually distinct from the primary action.
- Avoid competing with the main CTA.

ZenSurface

Responsibilities:

- Provide standard content-container styling.
- Use semantic surface colours.
- Apply consistent shape and spacing.
- Avoid unnecessary elevation.

ZenIllustration

Responsibilities:

- Render approved artwork with predictable sizing.
- Preserve aspect ratio.
- Support content descriptions when the artwork conveys meaning.
- Avoid accidental clipping or distortion.

ZenSectionHeader

Responsibilities:

- Establish clear section hierarchy.
- Use the appropriate editorial or UI typography.
- Maintain consistent spacing.

ZenEmptyState

Responsibilities:

- Explain why content is absent.
- Offer a useful next action where appropriate.
- Use supporting artwork sparingly.
- Never rely on illustration alone to explain the state.

ZenReminderCard

Responsibilities:

- Communicate reminder information clearly.
- Show the next relevant action.
- Support reminder states without excessive badges or visual noise.

ZenProgressIndicator

Responsibilities:

- Communicate progress accurately.
- Remain understandable without colour.
- Avoid gamified or competitive presentation.

ZenBottomNavigation

Responsibilities:

- Provide consistent navigation on supported Android layouts.
- Clearly indicate the selected destination.
- Avoid unnecessary labels or decorative indicators.
- Follow platform conventions where they improve usability.

8.2 Component states

Every interactive component must account for:

- Default.
- Pressed.
- Focused.
- Disabled.
- Loading, where applicable.
- Error, where applicable.
- Selected, where applicable.

Do not implement only the happy path.

8.3 Interaction rules

- A primary action should be visually obvious.
- Destructive actions must not resemble ordinary confirmation actions.
- Taps must produce appropriate feedback.
- Long-running operations need clear loading or progress states.
- Forms must explain validation errors near the relevant field.
- Controls must not silently fail.
- Avoid duplicate buttons that trigger the same action.
- Prevent accidental repeated hydration logging or reminder creation.
- Respect system back navigation.


---

9. Android screen design rules

The phone experience can accommodate editorial content, reflective prompts and richer artwork. That does not mean every screen should be visually elaborate.

9.1 Mindful Sip

Purpose: Create a calm, simple hydration interaction.

Recommended hierarchy:

1. A concise editorial heading.
2. One primary watercolor focal element.
3. Current hydration information, if included in the product requirements.
4. A clearly visible action to record a drink.
5. Minimal supporting information.

Rules:

- Keep the action immediately discoverable.
- Do not let the artwork push the primary action below an unnecessarily long scroll.
- Avoid multiple competing hydration metrics.
- Make the selected drink volume clear.
- Confirm successful logging through subtle feedback.
- Prevent duplicate records caused by repeated taps.

9.2 Focus Vessel

Purpose: Present the app's focus or mindful-practice experience, according to the implemented product requirements.

Rules:

- Establish one clear intention per screen.
- Use the Enso or another approved illustration as a focal element only when it adds meaning.
- Keep instructions short and actionable.
- Use calm typography and generous spacing.
- Avoid excessive timers, statistics or distracting motion.
- Make start, pause, resume and completion states explicit if the experience uses timed sessions.

9.3 Herbal Infusion Manual

Purpose: Make herbal infusion information easy to browse and understand.

Rules:

- Prioritise readability over decorative presentation.
- Use consistent hierarchy for ingredient names, preparation steps and supporting information.
- Use approved botanical or watercolour illustrations sparingly.
- Avoid placing essential instructions over textured artwork.
- Keep preparation steps sequential and scannable.
- Distinguish optional information from essential instructions.
- Do not invent health claims or imply that herbal drinks treat medical conditions.

9.4 Shared Android rules

- Every screen must use the same token system.
- All screen content must adapt to different device sizes.
- Text must remain usable at larger system font settings.
- Loading, empty, error and success states must be designed.
- Navigation must be predictable.
- The back button must behave consistently.
- Artwork must not block essential content.
- Do not introduce a different card, button or typography style for a single screen without justification.


---

10. Wear OS design system

Wear OS shares the brand language, not the Android layout.

The watch experience should answer one question quickly: What does the user need to know or do right now?

10.1 Watch-specific principles

- Glanceability comes before decoration.
- Use short labels.
- Keep one dominant action per view.
- Avoid dense information layouts.
- Minimise typing and multi-step interactions.
- Keep visual focus near the centre of the round display.
- Account for circular clipping and curved edges.
- Use restrained artwork and minimal texture.
- Avoid placing essential content near the screen perimeter.
- Use haptics only for meaningful events.
- Make the experience useful even when the phone is not immediately available, where the architecture supports it.

10.2 Wear OS visual adaptation

Element	Android	Wear OS

Background	Warm ash grey	Dark or muted ash variant, depending on display mode
Artwork	Larger watercolor compositions	Small, restrained motifs
Typography	Editorial serif + sans-serif	Sans-serif-led, highly legible
Navigation	Conventional screen navigation	Short, focused navigation
Cards	Comfortable content surfaces	Compact, purpose-specific containers
Actions	Multiple related actions may fit	One d