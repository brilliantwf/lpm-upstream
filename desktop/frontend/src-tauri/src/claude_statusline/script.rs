// Status line scripts (presets and the user's Custom line) are generated from a
// spec by `build_custom_statusline`, composing these shared sh building blocks —
// the exact idioms the presets always used (jqr, tint, meter, append, DIM/RESET).
use serde::{Deserialize, Serialize};

pub(super) const STATUSLINE_HEADER: &str = r##"#!/bin/sh
input=$(cat)
jqr() { printf '%s' "$input" | jq -r "$1"; }
"##;

/// Git for Windows ships no jq, and without it every line renders empty, so a
/// Windows line says what is missing instead.
pub(super) const STATUSLINE_NEEDS_JQ: &str = r##"command -v jq >/dev/null 2>&1 || { printf 'Install jq to see this status line (winget install jqlang.jq)'; exit 0; }
"##;

pub(super) const STATUSLINE_TINT_FN: &str = r##"tint() { if [ "$1" -ge 80 ]; then printf '\033[31m'; elif [ "$1" -ge 50 ]; then printf '\033[33m'; else printf '\033[32m'; fi; }
"##;

// Label-less meter: the caller prints the (optionally colored) label, then this
// prints the tinted bar + track + percentage. Width comes from `$MW`.
pub(super) const STATUSLINE_METER_FN: &str = r##"meter() {
    pct=$1 width=$MW
    units=$(( (pct * width * 2 + 50) / 100 ))
    [ "$units" -gt $((width * 2)) ] && units=$((width * 2)); [ "$units" -lt 0 ] && units=0
    full=$((units / 2)) half=$((units % 2))
    fill="" i=0
    while [ "$i" -lt "$full" ]; do fill="${fill}━"; i=$((i + 1)); done
    [ "$half" -eq 1 ] && fill="${fill}╸"
    track="" i=$((full + half))
    while [ "$i" -lt "$width" ]; do track="${track}━"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Equalizer meter: filled cells rise to full height, the fractional
// cell steps through the vertical eighths, the empty track sits flat and dim.
pub(super) const STATUSLINE_METER_FN_BLOCKS: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( pct * width / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    rem=$(( pct * width - filled * 100 ))
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}▇"; i=$((i + 1)); done
    used=$filled
    if [ "$used" -lt "$width" ] && [ "$rem" -ge 15 ]; then
        if [ "$rem" -ge 85 ]; then p=▇; elif [ "$rem" -ge 70 ]; then p=▆; elif [ "$rem" -ge 55 ]; then p=▅; elif [ "$rem" -ge 40 ]; then p=▄; elif [ "$rem" -ge 27 ]; then p=▃; else p=▂; fi
        fill="${fill}${p}"; used=$((used + 1))
    fi
    track="" i=$used
    while [ "$i" -lt "$width" ]; do track="${track}▁"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Dotted meter: filled ● up to the rounded percentage, hollow ○ for the rest.
pub(super) const STATUSLINE_METER_FN_DOTS: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( (pct * width + 50) / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}●"; i=$((i + 1)); done
    track="" i=$filled
    while [ "$i" -lt "$width" ]; do track="${track}○"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Segmented meter: solid ▰ up to the rounded percentage, hollow ▱ for the rest.
pub(super) const STATUSLINE_METER_FN_SEGMENTS: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( (pct * width + 50) / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}▰"; i=$((i + 1)); done
    track="" i=$filled
    while [ "$i" -lt "$width" ]; do track="${track}▱"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Square meter: filled ■ up to the rounded percentage, hollow □ for the rest.
pub(super) const STATUSLINE_METER_FN_SQUARES: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( (pct * width + 50) / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}■"; i=$((i + 1)); done
    track="" i=$filled
    while [ "$i" -lt "$width" ]; do track="${track}□"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Shaded meter: dense ▓ cells, one medium ▒ cell for the fractional part, and a
// light ░ track.
pub(super) const STATUSLINE_METER_FN_SHADE: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( pct * width / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    rem=$(( pct * width - filled * 100 ))
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}▓"; i=$((i + 1)); done
    used=$filled
    if [ "$used" -lt "$width" ] && [ "$rem" -ge 30 ]; then
        fill="${fill}▒"; used=$((used + 1))
    fi
    track="" i=$used
    while [ "$i" -lt "$width" ]; do track="${track}░"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

// Braille meter: full ⣿ cells, the fractional cell steps through the eight
// braille dot counts, the empty track sits on a dim ⣀ baseline.
pub(super) const STATUSLINE_METER_FN_BRAILLE: &str = r##"meter() {
    pct=$1 width=$MW
    filled=$(( pct * width / 100 ))
    [ "$filled" -gt "$width" ] && filled=$width
    [ "$filled" -lt 0 ] && filled=0
    rem=$(( pct * width - filled * 100 ))
    fill="" i=0
    while [ "$i" -lt "$filled" ]; do fill="${fill}⣿"; i=$((i + 1)); done
    used=$filled
    if [ "$used" -lt "$width" ] && [ "$rem" -ge 12 ]; then
        if [ "$rem" -ge 88 ]; then p=⣷; elif [ "$rem" -ge 75 ]; then p=⣧; elif [ "$rem" -ge 62 ]; then p=⣇; elif [ "$rem" -ge 50 ]; then p=⡇; elif [ "$rem" -ge 38 ]; then p=⡆; elif [ "$rem" -ge 25 ]; then p=⡄; else p=⡀; fi
        fill="${fill}${p}"; used=$((used + 1))
    fi
    track="" i=$used
    while [ "$i" -lt "$width" ]; do track="${track}⣀"; i=$((i + 1)); done
    printf '%b%s%b%b%s%b %s%%' "$(tint "$pct")" "$fill" "$RESET" "$DIM" "$track" "$RESET" "$pct"
}
"##;

pub(super) const STATUSLINE_APPEND_FN: &str = r##"out=""
append() { if [ -n "$out" ]; then out="${out}${SEP}${1}"; else out="$1"; fi; }
"##;
/// One segment of a status line: `id` picks the source, `color` optionally tints
/// it, `label` overrides the value prefix, and `text` carries free text.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Segment {
    pub id: String,
    #[serde(default = "default_color")]
    pub color: String,
    #[serde(default)]
    pub text: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub label: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub icon: Option<String>,
}

pub(super) fn default_color() -> String {
    "default".into()
}

/// Accept both the current object form and the legacy plain-string array (old
/// custom.json / older frontend payloads), normalizing everything to objects.
pub(super) fn deserialize_segments<'de, D>(d: D) -> Result<Vec<Segment>, D::Error>
where
    D: serde::Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum In {
        Plain(String),
        Full(Segment),
    }
    let raw = Vec::<In>::deserialize(d)?;
    Ok(raw
        .into_iter()
        .map(|x| match x {
            In::Plain(id) => Segment {
                id,
                color: default_color(),
                text: String::new(),
                label: None,
                icon: None,
            },
            In::Full(s) => s,
        })
        .collect())
}

/// A user-composable status line: an ordered list of segments, a separator token,
/// how usage segments render, and the meter width. The presets are just fixed
/// specs (see `preset_spec`), so `build_custom_statusline` is the single generator.
#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
#[serde(rename_all = "camelCase")]
pub struct CustomSpec {
    #[serde(deserialize_with = "deserialize_segments")]
    pub segments: Vec<Segment>,
    pub separator: String,
    pub meter_style: String,
    #[serde(default = "default_meter_width")]
    pub meter_width: u32,
    /// Prefix each segment with an emoji glyph (📁 🌿 ✳ …).
    #[serde(default)]
    pub icons: bool,
    /// Decorate the git branch with a dirty marker and ahead/behind counts.
    #[serde(default)]
    pub git_status: bool,
}

pub(super) fn default_meter_width() -> u32 {
    7
}

/// The emoji glyph shown before a segment when `icons` is on. Empty for ids that
/// read better bare (the free-text segment carries its own leading glyph).
pub(super) fn default_segment_icon(id: &str) -> &'static str {
    match id {
        "folder" => "📁",
        "path" => "📂",
        "model" => "✳",
        "branch" => "🌿",
        "ctx" => "🧠",
        "five" => "⚡",
        "seven" => "📆",
        "cost" => "💰",
        _ => "",
    }
}

pub(super) fn segment_icon(segment: &Segment) -> &str {
    segment
        .icon
        .as_deref()
        .unwrap_or_else(|| default_segment_icon(&segment.id))
}

pub(super) fn segment_label<'a>(segment: &'a Segment, default: &'a str) -> &'a str {
    segment.label.as_deref().unwrap_or(default)
}

pub(super) fn segment_label_prefix(segment: &Segment, default: &str) -> String {
    let label = segment_label(segment, default);
    if label.is_empty() {
        String::new()
    } else {
        format!("{label} ")
    }
}

pub(super) const SEGMENT_IDS: [&str; 9] = [
    "folder", "path", "model", "branch", "ctx", "five", "seven", "cost", "text",
];

pub(super) const SEGMENT_COLORS: [&str; 9] = [
    "default", "dim", "red", "green", "yellow", "blue", "magenta", "cyan", "claude",
];

pub(super) const METER_STYLES: [&str; 8] = [
    "bar", "blocks", "shade", "segments", "dots", "squares", "braille", "percent",
];

pub(super) fn seg(id: &str) -> Segment {
    Segment {
        id: id.into(),
        color: default_color(),
        text: String::new(),
        label: None,
        icon: None,
    }
}

pub(super) fn colseg(id: &str, color: &str) -> Segment {
    Segment {
        id: id.into(),
        color: color.into(),
        text: String::new(),
        label: None,
        icon: None,
    }
}

pub(super) fn default_custom_spec() -> CustomSpec {
    CustomSpec {
        segments: vec![
            seg("folder"),
            colseg("model", "claude"),
            seg("ctx"),
            seg("five"),
            seg("seven"),
            colseg("cost", "yellow"),
        ],
        separator: "·".into(),
        meter_style: "bar".into(),
        meter_width: 7,
        icons: true,
        git_status: false,
    }
}

pub(super) fn preset_spec(id: &str) -> Option<CustomSpec> {
    let (segments, icons) = match id {
        "minimal" => (
            vec![
                seg("folder"),
                seg("model"),
                seg("ctx"),
                seg("five"),
                seg("seven"),
                seg("cost"),
            ],
            false,
        ),
        "context" => (
            vec![
                seg("folder"),
                colseg("model", "claude"),
                seg("ctx"),
                seg("cost"),
            ],
            false,
        ),
        "meters" => (
            vec![
                seg("folder"),
                colseg("model", "claude"),
                seg("ctx"),
                seg("five"),
                seg("seven"),
                colseg("cost", "yellow"),
            ],
            false,
        ),
        "vibrant" => (
            vec![
                colseg("folder", "blue"),
                colseg("model", "claude"),
                seg("ctx"),
                seg("five"),
                seg("seven"),
                colseg("cost", "yellow"),
            ],
            true,
        ),
        _ => return None,
    };
    Some(CustomSpec {
        segments,
        separator: "·".into(),
        meter_style: "bar".into(),
        meter_width: 7,
        icons,
        git_status: false,
    })
}

/// The ANSI open sequence for a chosen color, or None for "default". Emitted as a
/// literal `\033[Nm` that the script's final `printf '%b'` turns into a real ESC.
/// "claude" is Claude's brand orange as a truecolor escape (not in the ANSI 16).
pub(super) fn color_open(color: &str) -> Option<String> {
    let code = match color {
        "dim" => "2",
        "red" => "31",
        "green" => "32",
        "yellow" => "33",
        "blue" => "94",
        "magenta" => "35",
        "cyan" => "36",
        "claude" => "38;2;217;119;87",
        _ => return None,
    };
    Some(format!("\\033[{code}m"))
}

/// The open sequence for a segment: the chosen color if any, else the segment's
/// natural style (`${DIM}` for normally-dim segments, empty otherwise).
pub(super) fn open_seq(color: &str, default_dim: bool) -> String {
    match color_open(color) {
        Some(c) => c,
        None if default_dim => "${DIM}".into(),
        None => String::new(),
    }
}

pub(super) fn close_seq(open: &str) -> &'static str {
    if open.is_empty() {
        ""
    } else {
        "${RESET}"
    }
}

pub(super) fn meter_append(var: &str, label: &str, jq: &str, style: &str, color: &str, icon: &str) -> String {
    // The icon and label take the segment color (or dim); the bar/number keep their tint.
    let lopen = open_seq(color, true);
    let label = if label.is_empty() {
        String::new()
    } else {
        format!("{label} ")
    };
    let prefix = format!("{icon}{label}");
    let compute = format!("{var}=$(jqr '{jq} // empty | round')\n");
    let body = if style == "percent" {
        format!("[ -n \"${var}\" ] && append \"${{RESET}}{lopen}{prefix}${{RESET}}$(tint \"${var}\")${{{var}}}%${{RESET}}\"\n")
    } else {
        format!("[ -n \"${var}\" ] && append \"${{RESET}}{lopen}{prefix}${{RESET}}$(meter \"${var}\")\"\n")
    };
    format!("{compute}{body}")
}

// Branch with git status: dirty marker (✳) plus ahead/behind counts vs upstream.
// Placeholders are substituted so we never fight `format!`'s brace escaping over
// the many `${...}` shell expansions inside.
pub(super) const BRANCH_GIT_STATUS: &str = r##"gitdir=$(jqr '.cwd // "."')
branch=$(git -C "$gitdir" branch --show-current 2>/dev/null)
if [ -n "$branch" ]; then
  gs=""
  [ -n "$(git -C "$gitdir" status --porcelain 2>/dev/null | head -c 1)" ] && gs="✳"
  ab=$(git -C "$gitdir" rev-list --left-right --count HEAD...@{upstream} 2>/dev/null)
  ahead=$(printf '%s' "$ab" | cut -f1); behind=$(printf '%s' "$ab" | cut -f2)
  case "$ahead" in ''|*[!0-9]*) ahead=0;; esac
  case "$behind" in ''|*[!0-9]*) behind=0;; esac
  [ "$ahead" -gt 0 ] && gs="${gs}↑${ahead}"
  [ "$behind" -gt 0 ] && gs="${gs}↓${behind}"
  append "__OPEN____IC____LABEL__${branch}${gs}__CLOSE__"
fi
"##;

pub(super) fn segment_snippet(segment: &Segment, style: &str, icons: bool, git_status: bool) -> String {
    let color = segment.color.as_str();
    let id = segment.id.as_str();
    // Emoji prefix, only when icons are on and this segment has one.
    let ic = if icons && !segment_icon(segment).is_empty() {
        format!("{} ", segment_icon(segment))
    } else {
        String::new()
    };
    match id {
        "folder" => {
            let o = open_seq(color, false);
            let label = segment_label_prefix(segment, "");
            format!(
                "cwd=$(basename \"$(jqr '.cwd // \".\"')\")\n[ -n \"$cwd\" ] && append \"{o}{ic}{label}$cwd{c}\"\n",
                c = close_seq(&o)
            )
        }
        "path" => {
            let o = open_seq(color, false);
            let label = segment_label_prefix(segment, "");
            format!(
                "cwd_full=$(jqr '.cwd // empty')\ncase \"$cwd_full\" in \"$HOME\"*) path=\"~${{cwd_full#$HOME}}\";; *) path=\"$cwd_full\";; esac\n[ -n \"$path\" ] && append \"{o}{ic}{label}$path{c}\"\n",
                c = close_seq(&o)
            )
        }
        "model" => {
            let o = open_seq(color, true);
            let label = segment_label_prefix(segment, "");
            format!(
                "model=$(jqr '.model.display_name // empty')\n[ -n \"$model\" ] && append \"{o}{ic}{label}$model{c}\"\n",
                c = close_seq(&o)
            )
        }
        "branch" => {
            let o = open_seq(color, true);
            let c = close_seq(&o);
            let label = segment_label_prefix(segment, "");
            if git_status {
                BRANCH_GIT_STATUS
                    .replace("__IC__", &ic)
                    .replace("__LABEL__", &label)
                    .replace("__OPEN__", &o)
                    .replace("__CLOSE__", c)
            } else {
                format!(
                    "branch=$(git -C \"$(jqr '.cwd // \".\"')\" branch --show-current 2>/dev/null)\n[ -n \"$branch\" ] && append \"{o}{ic}{label}$branch{c}\"\n"
                )
            }
        }
        "ctx" => {
            let label = segment_label_prefix(segment, "ctx");
            match color_open(color) {
                Some(cc) => format!(
                    "ctx=$(jqr '.context_window.remaining_percentage // empty | round')\n[ -n \"$ctx\" ] && append \"{cc}{ic}{label}${{ctx}}%${{RESET}}\"\n"
                ),
                None => format!(
                    "ctx=$(jqr '.context_window.remaining_percentage // empty | round')\n[ -n \"$ctx\" ] && append \"${{DIM}}{ic}{label}${{RESET}}${{ctx}}%\"\n"
                ),
            }
        }
        "cost" => {
            let o = open_seq(color, false);
            let label = segment_label_prefix(segment, "");
            format!(
                "cost_raw=$(jqr '.cost.total_cost_usd // empty')\n[ -n \"$cost_raw\" ] && cost=$(printf '%.2f' \"$cost_raw\" 2>/dev/null) || cost=\"\"\n[ -n \"$cost\" ] && append \"{o}{ic}{label}\\$${{cost}}{c}\"\n",
                c = close_seq(&o)
            )
        }
        "text" => {
            let o = open_seq(color, false);
            format!(
                "append \"{o}{ic}{t}{c}\"\n",
                t = segment.text,
                c = close_seq(&o)
            )
        }
        "five" => meter_append(
            "five",
            segment_label(segment, "5h"),
            ".rate_limits.five_hour.used_percentage",
            style,
            color,
            &ic,
        ),
        "seven" => meter_append(
            "seven",
            segment_label(segment, "7d"),
            ".rate_limits.seven_day.used_percentage",
            style,
            color,
            &ic,
        ),
        _ => String::new(),
    }
}

/// Generate the POSIX-sh status line script for `spec`, or an error describing an
/// invalid spec (empty/unknown/duplicate segment, bad separator, color, text, or
/// meter style). Multiple "text" segments are allowed; other ids are unique.
pub(super) fn build_custom_statusline(spec: &CustomSpec) -> Result<String, String> {
    if spec.segments.is_empty() {
        return Err("Pick at least one thing to show.".into());
    }
    let mut seen = std::collections::HashSet::new();
    for s in &spec.segments {
        if !SEGMENT_IDS.contains(&s.id.as_str()) {
            return Err(format!("Unknown status line item: {}", s.id));
        }
        if s.id != "text" && !seen.insert(s.id.as_str()) {
            return Err(format!("Duplicate status line item: {}", s.id));
        }
        if !SEGMENT_COLORS.contains(&s.color.as_str()) {
            return Err(format!("Unknown color: {}", s.color));
        }
        if s.id == "text" {
            if s.text.trim().is_empty() {
                return Err("Text items need some text.".into());
            }
            if s.text.contains(['"', '$', '`', '\\']) {
                return Err("Text uses an unsupported character.".into());
            }
        }
        if let Some(label) = &s.label {
            if label.chars().count() > 32 {
                return Err("Labels must be 32 characters or fewer.".into());
            }
            if !label.is_empty() && label.trim().is_empty() {
                return Err("Labels must contain visible text or be empty.".into());
            }
            if label.trim() != label {
                return Err("Labels cannot start or end with spaces.".into());
            }
            if label.contains(['"', '$', '`', '\\']) || label.chars().any(char::is_control) {
                return Err("Label uses an unsupported character.".into());
            }
        }
        if let Some(icon) = &s.icon {
            if icon.chars().count() > 16 {
                return Err("Icons must be one emoji or a short symbol.".into());
            }
            if !icon.is_empty() && icon.trim().is_empty() {
                return Err("Icons must contain a visible symbol.".into());
            }
            if icon.trim() != icon {
                return Err("Icons cannot start or end with spaces.".into());
            }
            if icon.contains(['"', '$', '`', '\\']) || icon.chars().any(char::is_control) {
                return Err("Icon uses an unsupported character.".into());
            }
        }
    }
    let style = spec.meter_style.as_str();
    if !METER_STYLES.contains(&style) {
        return Err(format!("Unknown meter style: {}", spec.meter_style));
    }
    let sep = spec.separator.trim();
    let sep_len = sep.chars().count();
    if !(1..=3).contains(&sep_len) {
        return Err("Separator must be 1 to 3 characters.".into());
    }
    if sep.contains(['"', '$', '`', '\\']) {
        return Err("Separator uses an unsupported character.".into());
    }
    let width = spec.meter_width.clamp(3, 16);

    let has_rate = spec
        .segments
        .iter()
        .any(|s| s.id == "five" || s.id == "seven");
    let needs_meter = has_rate && style != "percent";

    let mut out = String::new();
    out.push_str(STATUSLINE_HEADER);
    if cfg!(windows) {
        out.push_str(STATUSLINE_NEEDS_JQ);
    }
    out.push_str("DIM='\\033[2m'; RESET='\\033[0m'; SEP=\" ${DIM}");
    out.push_str(sep);
    out.push_str("${RESET} \"\n");
    if needs_meter {
        out.push_str(&format!("MW={width}\n"));
    }
    if has_rate {
        out.push_str(STATUSLINE_TINT_FN);
    }
    if needs_meter {
        out.push_str(match style {
            "blocks" => STATUSLINE_METER_FN_BLOCKS,
            "shade" => STATUSLINE_METER_FN_SHADE,
            "segments" => STATUSLINE_METER_FN_SEGMENTS,
            "dots" => STATUSLINE_METER_FN_DOTS,
            "squares" => STATUSLINE_METER_FN_SQUARES,
            "braille" => STATUSLINE_METER_FN_BRAILLE,
            _ => STATUSLINE_METER_FN,
        });
    }
    out.push_str(STATUSLINE_APPEND_FN);
    for s in &spec.segments {
        out.push_str(&segment_snippet(s, style, spec.icons, spec.git_status));
    }
    out.push_str("printf '%b' \"$out\"\n");
    Ok(out)
}

