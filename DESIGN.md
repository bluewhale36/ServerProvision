# Design System Inspired by Notion

## 1. Visual Theme & Atmosphere

Notion's website embodies the philosophy of the tool itself: a blank canvas that gets out of your way. The design system is built on warm neutrals rather than cold grays, creating a distinctly approachable minimalism that feels like quality paper rather than sterile glass. The page canvas is pure white (`#ffffff`) but the text isn't pure black -- it's a warm near-black (`rgba(0,0,0,0.95)`) that softens the reading experience imperceptibly. The warm gray scale (`#f6f5f4`, `#31302e`, `#615d59`, `#a39e98`) carries subtle yellow-brown undertones, giving the interface a tactile, almost analog warmth.

The custom NotionInter font (a modified Inter) is the backbone of the system. At display sizes (64px), it uses aggressive negative letter-spacing (-2.125px), creating headlines that feel compressed and precise. The weight range is broader than typical systems: 400 for body, 500 for UI elements, 600 for semi-bold labels, and 700 for display headings. OpenType features `"lnum"` (lining numerals) and `"locl"` (localized forms) are enabled on larger text, adding typographic sophistication that rewards close reading.

What makes Notion's visual language distinctive is its border philosophy. Rather than heavy borders or shadows, Notion uses ultra-thin `1px solid rgba(0,0,0,0.1)` borders -- borders that exist as whispers, barely perceptible division lines that create structure without weight. The shadow system is equally restrained: multi-layer stacks with cumulative opacity never exceeding 0.05, creating depth that's felt rather than seen.

**Key Characteristics:**
- NotionInter (modified Inter) with negative letter-spacing at display sizes (-2.125px at 64px)
- Warm neutral palette: grays carry yellow-brown undertones (`#f6f5f4` warm white, `#31302e` warm dark)
- Near-black text via `rgba(0,0,0,0.95)` -- not pure black, creating micro-warmth
- Ultra-thin borders: `1px solid rgba(0,0,0,0.1)` throughout -- whisper-weight division
- Multi-layer shadow stacks with sub-0.05 opacity for barely-there depth
- Notion Blue (`#0075de`) as the singular accent color for CTAs and interactive elements
- Pill badges (9999px radius) with tinted blue backgrounds for status indicators
- 8px base spacing unit with an organic, non-rigid scale

## 2. Color Palette & Roles

### Primary
- **Notion Black** (`rgba(0,0,0,0.95)` / `#000000f2`): Primary text, headings, body copy. The 95% opacity softens pure black without sacrificing readability.
- **Pure White** (`#ffffff`): Page background, card surfaces, button text on blue.
- **Notion Blue** (`#0075de`): Primary CTA, link color, interactive accent -- the only saturated color in the core UI chrome.

### Brand Secondary
- **Deep Navy** (`#213183`): Secondary brand color, used sparingly for emphasis and dark feature sections.
- **Active Blue** (`#005bab`): Button active/pressed state -- darker variant of Notion Blue.

### Warm Neutral Scale
- **Warm White** (`#f6f5f4`): Background surface tint, section alternation, subtle card fill. The yellow undertone is key.
- **Warm Dark** (`#31302e`): Dark surface background, dark section text. Warmer than standard grays.
- **Warm Gray 500** (`#615d59`): Secondary text, descriptions, muted labels.
- **Warm Gray 300** (`#a39e98`): Placeholder text, disabled states, caption text.

### Semantic Accent Colors
- **Teal** (`#2a9d99`): Success states, positive indicators.
- **Green** (`#1aae39`): Confirmation, completion badges.
- **Orange** (`#dd5b00`): Warning states, attention indicators.
- **Pink** (`#ff64c8`): Decorative accent, feature highlights.
- **Purple** (`#391c57`): Premium features, deep accents.
- **Brown** (`#523410`): Earthy accent, warm feature sections.

### Interactive
- **Link Blue** (`#0075de`): Primary link color with underline-on-hover.
- **Link Light Blue** (`#62aef0`): Lighter link variant for dark backgrounds.
- **Focus Blue** (`#097fe8`): Focus ring on interactive elements.
- **Badge Blue Bg** (`#f2f9ff`): Pill badge background, tinted blue surface.
- **Badge Blue Text** (`#097fe8`): Pill badge text, darker blue for readability.

### Shadows & Depth
- **Card Shadow** (`rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`): Multi-layer card elevation.
- **Deep Shadow** (`rgba(0,0,0,0.01) 0px 1px 3px, rgba(0,0,0,0.02) 0px 3px 7px, rgba(0,0,0,0.02) 0px 7px 15px, rgba(0,0,0,0.04) 0px 14px 28px, rgba(0,0,0,0.05) 0px 23px 52px`): Five-layer deep elevation for modals and featured content.
- **Whisper Border** (`1px solid rgba(0,0,0,0.1)`): Standard division border -- cards, dividers, sections.

## 3. Typography Rules

### Font Family
- **Primary**: `NotionInter`, with fallbacks: `Inter, -apple-system, system-ui, Segoe UI, Helvetica, Apple Color Emoji, Arial, Segoe UI Emoji, Segoe UI Symbol`
- **OpenType Features**: `"lnum"` (lining numerals) and `"locl"` (localized forms) enabled on display and heading text.

### Hierarchy

| Role | Font | Size | Weight | Line Height | Letter Spacing | Notes |
|------|------|------|--------|-------------|----------------|-------|
| Display Hero | NotionInter | 64px (4.00rem) | 700 | 1.00 (tight) | -2.125px | Maximum compression, billboard headlines |
| Display Secondary | NotionInter | 54px (3.38rem) | 700 | 1.04 (tight) | -1.875px | Secondary hero, feature headlines |
| Section Heading | NotionInter | 48px (3.00rem) | 700 | 1.00 (tight) | -1.5px | Feature section titles, with `"lnum"` |
| Sub-heading Large | NotionInter | 40px (2.50rem) | 700 | 1.50 | normal | Card headings, feature sub-sections |
| Sub-heading | NotionInter | 26px (1.63rem) | 700 | 1.23 (tight) | -0.625px | Section sub-titles, content headers |
| Card Title | NotionInter | 22px (1.38rem) | 700 | 1.27 (tight) | -0.25px | Feature cards, list titles |
| Body Large | NotionInter | 20px (1.25rem) | 600 | 1.40 | -0.125px | Introductions, feature descriptions |
| Body | NotionInter | 16px (1.00rem) | 400 | 1.50 | normal | Standard reading text |
| Body Medium | NotionInter | 16px (1.00rem) | 500 | 1.50 | normal | Navigation, emphasized UI text |
| Body Semibold | NotionInter | 16px (1.00rem) | 600 | 1.50 | normal | Strong labels, active states |
| Body Bold | NotionInter | 16px (1.00rem) | 700 | 1.50 | normal | Headlines at body size |
| Nav / Button | NotionInter | 15px (0.94rem) | 600 | 1.33 | normal | Navigation links, button text |
| Caption | NotionInter | 14px (0.88rem) | 500 | 1.43 | normal | Metadata, secondary labels |
| Caption Light | NotionInter | 14px (0.88rem) | 400 | 1.43 | normal | Body captions, descriptions |
| Badge | NotionInter | 12px (0.75rem) | 600 | 1.33 | 0.125px | Pill badges, tags, status labels |
| Micro Label | NotionInter | 12px (0.75rem) | 400 | 1.33 | 0.125px | Small metadata, timestamps |

### Principles
- **Compression at scale**: NotionInter at display sizes uses -2.125px letter-spacing at 64px, progressively relaxing to -0.625px at 26px and normal at 16px. The compression creates density at headlines while maintaining readability at body sizes.
- **Four-weight system**: 400 (body/reading), 500 (UI/interactive), 600 (emphasis/navigation), 700 (headings/display). The broader weight range compared to most systems allows nuanced hierarchy.
- **Warm scaling**: Line height tightens as size increases -- 1.50 at body (16px), 1.23-1.27 at sub-headings, 1.00-1.04 at display. This creates denser, more impactful headlines.
- **Badge micro-tracking**: The 12px badge text uses positive letter-spacing (0.125px) -- the only positive tracking in the system, creating wider, more legible small text.

## 4. Component Stylings

### Buttons

**Primary Blue**
- Background: `#0075de` (Notion Blue)
- Text: `#ffffff`
- Padding: 8px 16px
- Radius: 4px (subtle)
- Border: `1px solid transparent`
- Hover: background darkens to `#005bab`
- Active: scale(0.9) transform
- Focus: `2px solid` focus outline, `var(--shadow-level-200)` shadow
- Use: Primary CTA ("Get Notion free", "Try it")

**Secondary / Tertiary**
- Background: `rgba(0,0,0,0.05)` (translucent warm gray)
- Text: `#000000` (near-black)
- Padding: 8px 16px
- Radius: 4px
- Hover: text color shifts, scale(1.05)
- Active: scale(0.9) transform
- Use: Secondary actions, form submissions

**Ghost / Link Button**
- Background: transparent
- Text: `rgba(0,0,0,0.95)`
- Decoration: underline on hover
- Use: Tertiary actions, inline links

**Pill Badge Button**
- Background: `#f2f9ff` (tinted blue)
- Text: `#097fe8`
- Padding: 4px 8px
- Radius: 9999px (full pill)
- Font: 12px weight 600
- Use: Status badges, feature labels, "New" tags

### Cards & Containers
- Background: `#ffffff`
- Border: `1px solid rgba(0,0,0,0.1)` (whisper border)
- Radius: 12px (standard cards), 16px (featured/hero cards)
- Shadow: `rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.84688px, rgba(0,0,0,0.02) 0px 0.8px 2.925px, rgba(0,0,0,0.01) 0px 0.175px 1.04062px`
- Hover: subtle shadow intensification
- Image cards: 12px top radius, image fills top half

### Inputs & Forms
- Background: `#ffffff`
- Text: `rgba(0,0,0,0.9)`
- Border: `1px solid #dddddd`
- Padding: 6px
- Radius: 4px
- Focus: blue outline ring
- Placeholder: warm gray `#a39e98`

### Navigation
- Clean horizontal nav on white, not sticky
- Brand logo left-aligned (33x34px icon + wordmark)
- Links: NotionInter 15px weight 500-600, near-black text
- Hover: color shift to `var(--color-link-primary-text-hover)`
- CTA: blue pill button ("Get Notion free") right-aligned
- Mobile: hamburger menu collapse
- Product dropdowns with multi-level categorized menus

### Image Treatment
- Product screenshots with `1px solid rgba(0,0,0,0.1)` border
- Top-rounded images: `12px 12px 0px 0px` radius
- Dashboard/workspace preview screenshots dominate feature sections
- Warm gradient backgrounds behind hero illustrations (decorative character illustrations)

### Inline Code

인라인 `<code>` 요소는 배지(badge)와 유사한 시각적 처리를 하되, 코드 텍스트임을 명확히 구분한다.

- **Font**: `"SFMono-Regular", "Menlo", "Consolas", "Liberation Mono", monospace` — 모노스페이스 폰트로 일반 텍스트와 시각적으로 구분
- **Font Size**: 부모 텍스트 대비 약 85% (0.85em) — 모노스페이스 폰트의 시각적 크기 차이를 보정
- **Font Weight**: 500 — UI 요소로서의 존재감 확보
- **Background**: `rgba(0,0,0,0.06)` — Warm White 계열의 반투명 배경으로 배지처럼 텍스트가 요소 내에 담긴 느낌
- **Text Color**: `rgba(0,0,0,0.85)` — 본문보다 약간 연하게 처리하여 코드임을 암시
- **Padding**: `2px 6px` — 배지와 유사한 내부 여백으로 텍스트가 요소 안에 있는 형태
- **Radius**: `4px` — Pill 배지(9999px)보다 훨씬 작은 값으로, 사각형에 가까우면서 모서리만 살짝 둥근 형태. Micro radius 단계와 동일
- **Border**: `1px solid rgba(0,0,0,0.08)` — Whisper border보다 한 단계 더 미묘한 경계선
- **Vertical Align**: `baseline` 유지 — 주변 텍스트와 자연스러운 흐름

**Pill Badge vs Inline Code 비교:**

| 속성 | Pill Badge | Inline Code |
|------|-----------|-------------|
| Radius | 9999px (완전한 알약 형태) | 4px (사각형에 가까운 형태) |
| Font | NotionInter (본문과 동일) | Monospace (코드 전용) |
| Background | `#f2f9ff` (파란 틴트) | `rgba(0,0,0,0.06)` (중립 회색) |
| 용도 | 상태 표시, 태그, 라벨 | 코드 식별자, 기술 용어, 명령어 |

### Distinctive Components

**Feature Cards with Illustrations**
- Large illustrative headers (The Great Wave, product UI screenshots)
- 12px radius card with whisper border
- Title at 22px weight 700, description at 16px weight 400
- Warm white (`#f6f5f4`) background variant for alternating sections

**Trust Bar / Logo Grid**
- Company logos (trusted teams section) in their brand colors
- Horizontal scroll or grid layout with team counts
- Metric display: large number + description pattern

**Metric Cards**
- Large number display (e.g., "$4,200 ROI")
- NotionInter 40px+ weight 700 for the metric
- Description below in warm gray body text
- Whisper-bordered card container

## 5. Layout Principles

### Spacing System
- Base unit: 8px
- Scale: 2px, 3px, 4px, 5px, 6px, 7px, 8px, 11px, 12px, 14px, 16px, 24px, 32px
- Non-rigid organic scale with fractional values (5.6px, 6.4px) for micro-adjustments

### Grid & Container
- Max content width: approximately 1200px
- Hero: centered single-column with generous top padding (80-120px)
- Feature sections: 2-3 column grids for cards
- Full-width warm white (`#f6f5f4`) section backgrounds for alternation
- Code/dashboard screenshots as contained with whisper border

### Whitespace Philosophy
- **Generous vertical rhythm**: 64-120px between major sections. Notion lets content breathe with vast vertical padding.
- **Warm alternation**: White sections alternate with warm white (`#f6f5f4`) sections, creating gentle visual rhythm without harsh color breaks.
- **Content-first density**: Body text blocks are compact (line-height 1.50) but surrounded by ample margin, creating islands of readable content in a sea of white space.

### Border Radius Scale
- Micro (4px): Buttons, inputs, functional interactive elements
- Subtle (5px): Links, list items, menu items
- Standard (8px): Small cards, containers, inline elements
- Comfortable (12px): Standard cards, feature containers, image tops
- Large (16px): Hero cards, featured content, promotional blocks
- Full Pill (9999px): Badges, pills, status indicators
- Circle (100%): Tab indicators, avatars

## 6. Depth & Elevation

| Level | Treatment | Use |
|-------|-----------|-----|
| Flat (Level 0) | No shadow, no border | Page background, text blocks |
| Whisper (Level 1) | `1px solid rgba(0,0,0,0.1)` | Standard borders, card outlines, dividers |
| Soft Card (Level 2) | 4-layer shadow stack (max opacity 0.04) | Content cards, feature blocks |
| Deep Card (Level 3) | 5-layer shadow stack (max opacity 0.05, 52px blur) | Modals, featured panels, hero elements |
| Focus (Accessibility) | `2px solid var(--focus-color)` outline | Keyboard focus on all interactive elements |

**Shadow Philosophy**: Notion's shadow system uses multiple layers with extremely low individual opacity (0.01 to 0.05) that accumulate into soft, natural-looking elevation. The 4-layer card shadow spans from 1.04px to 18px blur, creating a gradient of depth rather than a single hard shadow. The 5-layer deep shadow extends to 52px blur at 0.05 opacity, producing ambient occlusion that feels like natural light rather than computer-generated depth. This layered approach makes elements feel embedded in the page rather than floating above it.

### Decorative Depth
- Hero section: decorative character illustrations (playful, hand-drawn style)
- Section alternation: white to warm white (`#f6f5f4`) background shifts
- No hard section borders -- separation comes from background color changes and spacing

## 7. Responsive Behavior

### Breakpoints
| Name | Width | Key Changes |
|------|-------|-------------|
| Mobile Small | <400px | Tight single column, minimal padding |
| Mobile | 400-600px | Standard mobile, stacked layout |
| Tablet Small | 600-768px | 2-column grids begin |
| Tablet | 768-1080px | Full card grids, expanded padding |
| Desktop Small | 1080-1200px | Standard desktop layout |
| Desktop | 1200-1440px | Full layout, maximum content width |
| Large Desktop | >1440px | Centered, generous margins |

### Touch Targets
- Buttons use comfortable padding (8px-16px vertical)
- Navigation links at 15px with adequate spacing
- Pill badges have 8px horizontal padding for tap targets
- Mobile menu toggle uses standard hamburger button

### Collapsing Strategy
- Hero: 64px display -> scales to 40px -> 26px on mobile, maintains proportional letter-spacing
- Navigation: horizontal links + blue CTA -> hamburger menu
- Feature cards: 3-column -> 2-column -> single column stacked
- Product screenshots: maintain aspect ratio with responsive images
- Trust bar logos: grid -> horizontal scroll on mobile
- Footer: multi-column -> stacked single column
- Section spacing: 80px+ -> 48px on mobile

### Image Behavior
- Workspace screenshots maintain whisper border at all sizes
- Hero illustrations scale proportionally
- Product screenshots use responsive images with consistent border radius
- Full-width warm white sections maintain edge-to-edge treatment

## 8. Accessibility & States

### Focus System
- All interactive elements receive visible focus indicators
- Focus outline: `2px solid` with focus color + shadow level 200
- Tab navigation supported throughout all interactive components
- High contrast text: near-black on white exceeds WCAG AAA (>14:1 ratio)

### Interactive States
- **Default**: Standard appearance with whisper borders
- **Hover**: Color shift on text, scale(1.05) on buttons, underline on links
- **Active/Pressed**: scale(0.9) transform, darker background variant
- **Focus**: Blue outline ring with shadow reinforcement
- **Disabled**: Warm gray (`#a39e98`) text, reduced opacity

### Color Contrast
- Primary text (rgba(0,0,0,0.95)) on white: ~18:1 ratio
- Secondary text (#615d59) on white: ~5.5:1 ratio (WCAG AA)
- Blue CTA (#0075de) on white: ~4.6:1 ratio (WCAG AA for large text)
- Badge text (#097fe8) on badge bg (#f2f9ff): ~4.5:1 ratio (WCAG AA for large text)

## 9. Agent Prompt Guide

### Quick Color Reference
- Primary CTA: Notion Blue (`#0075de`)
- Background: Pure White (`#ffffff`)
- Alt Background: Warm White (`#f6f5f4`)
- Heading text: Near-Black (`rgba(0,0,0,0.95)`)
- Body text: Near-Black (`rgba(0,0,0,0.95)`)
- Secondary text: Warm Gray 500 (`#615d59`)
- Muted text: Warm Gray 300 (`#a39e98`)
- Border: `1px solid rgba(0,0,0,0.1)`
- Link: Notion Blue (`#0075de`)
- Focus ring: Focus Blue (`#097fe8`)

### Example Component Prompts
- "Create a hero section on white background. Headline at 64px NotionInter weight 700, line-height 1.00, letter-spacing -2.125px, color rgba(0,0,0,0.95). Subtitle at 20px weight 600, line-height 1.40, color #615d59. Blue CTA button (#0075de, 4px radius, 8px 16px padding, white text) and ghost button (transparent bg, near-black text, underline on hover)."
- "Design a card: white background, 1px solid rgba(0,0,0,0.1) border, 12px radius. Use shadow stack: rgba(0,0,0,0.04) 0px 4px 18px, rgba(0,0,0,0.027) 0px 2.025px 7.85px, rgba(0,0,0,0.02) 0px 0.8px 2.93px, rgba(0,0,0,0.01) 0px 0.175px 1.04px. Title at 22px NotionInter weight 700, letter-spacing -0.25px. Body at 16px weight 400, color #615d59."
- "Build a pill badge: #f2f9ff background, #097fe8 text, 9999px radius, 4px 8px padding, 12px NotionInter weight 600, letter-spacing 0.125px."
- "Create navigation: white header. NotionInter 15px weight 600 for links, near-black text. Blue pill CTA 'Get Notion free' right-aligned (#0075de bg, white text, 4px radius)."
- "Design an alternating section layout: white sections alternate with warm white (#f6f5f4) sections. Each section has 64-80px vertical padding, max-width 1200px centered. Section heading at 48px weight 700, line-height 1.00, letter-spacing -1.5px."

### Iteration Guide
1. Always use warm neutrals -- Notion's grays have yellow-brown undertones (#f6f5f4, #31302e, #615d59, #a39e98), never blue-gray
2. Letter-spacing scales with font size: -2.125px at 64px, -1.875px at 54px, -0.625px at 26px, normal at 16px
3. Four weights: 400 (read), 500 (interact), 600 (emphasize), 700 (announce)
4. Borders are whispers: 1px solid rgba(0,0,0,0.1) -- never heavier
5. Shadows use 4-5 layers with individual opacity never exceeding 0.05
6. The warm white (#f6f5f4) section background is essential for visual rhythm
7. Pill badges (9999px) for status/tags, 4px radius for buttons and inputs
8. Notion Blue (#0075de) is the only saturated color in core UI -- use it sparingly for CTAs and links

## 10. CSS Class Usage Constraints

이 섹션은 커스텀 CSS 클래스(n- 접두사)의 **사용 맥락 규칙**을 정의한다. 섹션 4(Component Stylings)가 "어떻게 보이는가"를 다루는 반면, 이 섹션은 "언제/어디서 사용하는가"를 다룬다. 이름만으로 용도가 자명한 클래스(n-btn-primary, n-card 등)는 생략하고, 오용 가능성이 있는 클래스만 선별하여 문서화한다.

### 10.1 데이터 의미 셀 클래스

테이블 셀(`<td>`)에 적용하는 클래스 중, 특정 데이터 유형에만 허용되는 것들이다.

**`n-td-id`** — 레코드의 기본 키(PK) 또는 고유 식별자를 표시하는 셀에만 사용한다.

- 스타일: 가운데 정렬, monospace, 13px, muted 색상
- 올바른 사용: `<td class="n-td-id" th:text="${setting.id}">1</td>`
- 잘못된 사용: `<td class="n-td-id" th:text="${partition.mountPoint}">/boot</td>` — 마운트포인트는 ID가 아니다. monospace가 필요하면 `n-table-mono`를 사용한다.

**`n-table-mono`** — 코드성 값(MAC 주소, 파일 경로, 버전 문자열 등)을 표시하는 범용 monospace 클래스. `n-td-id`와 달리 색상이나 정렬을 변경하지 않으므로, ID가 아닌 코드값에는 이 클래스를 사용한다.

**`n-table-muted`** — 실제 값이 없을 때의 대체 텍스트(`-`, `없음`, `미입력`)에만 사용한다. 실제 데이터가 있는 셀에는 절대 적용하지 않는다.

**`n-table-empty`** — 테이블에 행이 0개일 때 "데이터 없음" 메시지를 표시하는 단일 `<tr>`에만 사용한다. `<td>`에는 헤더 수와 동일한 `colspan`을 지정해야 한다.

### 10.2 테이블 타입 선택

두 테이블 클래스는 **상호 배타적**이며, 페이지의 맥락에 따라 선택한다.

| 클래스 | 기반 | 사용 맥락 | 선택 기준 |
|--------|------|----------|----------|
| `n-table` | style.css 커스텀 | 읽기 전용 목록 (dashboard, list 페이지) | 데이터를 조회/탐색하는 컨텍스트 |
| `n-tbl-list` | Bootstrap `.table` | 폼 내 인라인 편집 (new, edit 페이지) | 행에 input, select, 삭제 버튼이 포함된 컨텍스트 |

규칙:
- 하나의 `<table>`에 두 클래스를 동시에 적용하지 않는다.
- `n-tbl-list`는 반드시 Bootstrap 클래스(`table table-sm table-bordered`)와 함께 사용한다.

### 10.3 컨텍스트 스타일 오버라이드

부모 클래스가 자식 요소의 스타일을 **암묵적으로 변경**하는 패턴이다. 이 클래스들은 적용 맥락을 엄격히 지켜야 의도치 않은 스타일 변형을 방지할 수 있다.

**`n-detail-fields`** — 상세(읽기 전용) 페이지에서 필드 그룹을 감싸는 래퍼 `<div>`.

- 효과: 내부 `.n-label:not(.strong)`을 11px/uppercase/muted로 오버라이드한다.
- 올바른 사용: `setting/detail.html` 등 읽기 전용 상세 페이지의 필드 블록
- 잘못된 사용: 편집 폼(`new.html`, `edit.html`)의 폼 그룹을 이 클래스로 감싸면 레이블이 너무 작아져 폼 맥락에 부적합해진다.

**`n-detail-table`** — 2열 key-value 구조의 상세 정보 테이블.

- 1열(`<th>`): 필드명 레이블, 2열(`<td>`): 값
- `.mono` 수식자는 코드/경로 값에만 적용
- 잘못된 사용: 다건 목록 테이블에 이 클래스를 사용하면 `<th>` 너비가 180px로 고정되어 레이아웃이 깨진다.

### 10.4 페이지 스코프 컴포넌트

특정 페이지 전용 CSS 파일에 정의된 클래스들이다. 해당 페이지 외부에서 사용하면 스타일이 적용되지 않는다.

**`n-miller-*`** (18개 클래스) — OS 목록 페이지(`admin/os/os-list.html`) 전용 Miller Columns 컴포넌트.

- 정의 파일: `css/miller.css` (os-list 페이지에서만 로드)
- 규칙: 개별 miller 클래스를 다른 페이지에서 추출하여 사용하지 않는다. 유사한 다단 선택 UI가 필요하면 `n-miller` 컴포넌트 구조 전체를 재사용한다.

**`n-extract-task-*`**, **`n-spinner`**, **`n-progress-bar-*`** — OS 관리 비동기 추출 기능 전용.

- 정의 파일: `admin/os/os-list.css` (os-list 페이지에서만 로드)
- 규칙: 다른 페이지에서 이 클래스를 참조하려면, 먼저 CSS를 글로벌 스타일시트로 이동해야 한다.

### 10.5 시맨틱 레이아웃 컨테이너

레이아웃 내에서 고정된 역할을 갖는 컨테이너 클래스다. 역할 외 맥락에서 사용하면 마진/패딩/테두리가 의도와 다르게 적용된다.

**`n-section`** — 폼 또는 상세 카드 내에서 논리적 그룹을 구분하는 시각적 구분선.

- 위치: `n-card-body`의 직접 자식으로만 사용
- 잘못된 사용: `n-row` 내부에 중첩하거나, 범용 제목 요소로 사용

**`n-form-actions`** — 폼 카드 하단의 최종 액션 바(저장/취소 버튼).

- 규칙: 폼당 정확히 1개. 상단 테두리와 상단 여백을 포함한다.
- 잘못된 사용: 폼 중간에 인라인 액션 그룹이 필요하면 `n-actions`를 사용한다.

**`n-page-header`** — 페이지 최상단의 제목 + 액션 버튼 영역.

- 구조: 좌측 `n-page-title` + 우측 `n-header-actions`
- 규칙: 페이지당 정확히 1개, 메인 카드 앞에 배치
