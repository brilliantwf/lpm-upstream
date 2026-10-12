use super::*;
use crate::hooks::test_support::*;

fn slt_dirs() -> (tempfile::TempDir, PathBuf, PathBuf) {
    let td = tempfile::tempdir().unwrap();
    let settings = td.path().join("settings.json");
    let sldir = td.path().join("statuslines");
    (td, settings, sldir)
}

fn read_json(path: &Path) -> Value {
    serde_json::from_slice(&std::fs::read(path).unwrap()).unwrap()
}

const SAMPLE_PAYLOAD: &str = r#"{"model":{"display_name":"Opus 4.8"},"cwd":"/tmp/proj","context_window":{"remaining_percentage":72},"rate_limits":{"five_hour":{"used_percentage":34},"seven_day":{"used_percentage":62}},"cost":{"total_cost_usd":4.2}}"#;

fn run_script(source: &str, payload: &str) -> String {
    let td = tempfile::tempdir().unwrap();
    let script = write_template_script(td.path(), "probe", source).unwrap();
    let out = std::process::Command::new(posix_sh())
        .arg(&script)
        .stdin(std::process::Stdio::piped())
        .stdout(std::process::Stdio::piped())
        .spawn()
        .and_then(|mut c| {
            use std::io::Write;
            c.stdin.take().unwrap().write_all(payload.as_bytes())?;
            c.wait_with_output()
        })
        .unwrap();
    String::from_utf8_lossy(&out.stdout).into_owned()
}

#[cfg(windows)]
#[test]
fn a_line_without_jq_says_so_instead_of_rendering_empty() {
    let td = tempfile::tempdir().unwrap();
    let source = build_custom_statusline(&preset_spec("minimal").unwrap()).unwrap();
    let script = write_template_script(td.path(), "probe", &source).unwrap();
    let out = std::process::Command::new(posix_sh())
        .arg("-c")
        .arg(format!(
            "PATH=/nowhere; . {}",
            sh_quote(&crate::hookform::script_path(&script))
        ))
        .stdin(std::process::Stdio::null())
        .output()
        .unwrap();
    let text = String::from_utf8_lossy(&out.stdout);
    assert!(text.starts_with("Install jq"), "{text:?}");
}

#[test]
fn template_scripts_render_sensibly() {
    let cases: &[(&str, &[&str], bool, &str)] = &[
        (
            "minimal",
            &["folder", "model", "ctx", "five", "seven", "cost"],
            true,
            "default",
        ),
        (
            "context",
            &["folder", "model", "ctx", "cost"],
            false,
            "claude",
        ),
        (
            "meters",
            &["folder", "model", "ctx", "five", "seven", "cost"],
            true,
            "claude",
        ),
    ];
    for (id, expected_ids, has_usage, model_color) in cases {
        let spec = preset_spec(id).unwrap();
        assert_eq!(
            spec.segments
                .iter()
                .map(|segment| segment.id.as_str())
                .collect::<Vec<_>>(),
            *expected_ids
        );
        assert_eq!(spec.segments[0].color, "default");
        assert_eq!(spec.segments[1].color, *model_color);
        assert_eq!(spec.separator, "·");
        assert_eq!(spec.meter_style, "bar");
        assert_eq!(spec.meter_width, 7);
        assert!(!spec.icons);
        assert!(!spec.git_status);
        let source = build_custom_statusline(&spec).unwrap();
        let text = run_script(&source, SAMPLE_PAYLOAD);
        assert!(text.contains("proj"), "{id} shows the folder: {text:?}");
        assert!(text.contains("Opus 4.8"), "{id} shows the model: {text:?}");
        assert!(text.contains("72%"), "{id} shows context: {text:?}");
        assert!(!text.contains("📁"), "{id} has no icons: {text:?}");
        if *has_usage {
            assert!(
                text.contains("34%") && text.contains("62%"),
                "{id} shows usage: {text:?}"
            );
        }
        assert!(text.contains("$4.20"), "{id} shows session cost: {text:?}");
        if *id == "meters" {
            assert_eq!(spec.segments.last().unwrap().color, "yellow");
            assert!(
                text.contains("\u{1b}[33m$4.20"),
                "meters show session cost in yellow: {text:?}"
            );
        }
    }
}

fn cspec(ids: &[&str], sep: &str, style: &str) -> CustomSpec {
    CustomSpec {
        segments: ids.iter().map(|s| seg(s)).collect(),
        separator: sep.into(),
        meter_style: style.into(),
        meter_width: 7,
        icons: false,
        git_status: false,
    }
}

#[test]
fn custom_script_renders_segments_in_order_with_separator() {
    let text = run_script(
        &build_custom_statusline(&cspec(&["model", "five", "folder"], "|", "bar")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    let model = text.find("Opus 4.8").unwrap();
    let five = text.find("34%").unwrap();
    let folder = text.find("proj").unwrap();
    assert!(
        model < five && five < folder,
        "spec order preserved: {text:?}"
    );
    assert!(text.contains('|'), "uses the chosen separator: {text:?}");
}

#[test]
fn custom_percent_style_has_no_bar() {
    let text = run_script(
        &build_custom_statusline(&cspec(&["five", "seven"], "·", "percent")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(
        text.contains("34%") && text.contains("62%"),
        "shows plain percentages: {text:?}"
    );
    assert!(
        !text.contains('━'),
        "percent style draws no meter bar: {text:?}"
    );
}

#[test]
fn custom_branch_and_cost_degrade_when_absent() {
    // No cost and a cwd that is not a git repo -> both segments drop out.
    let payload = r#"{"cwd":"/tmp","model":{"display_name":"X"}}"#;
    let text = run_script(
        &build_custom_statusline(&cspec(&["folder", "branch", "cost"], "·", "bar")).unwrap(),
        payload,
    );
    assert!(!text.contains('$'), "cost absent: {text:?}");
    assert!(!text.contains('·'), "no dangling separators: {text:?}");
}

#[test]
fn custom_cost_renders_when_present() {
    let text = run_script(
        &build_custom_statusline(&cspec(&["cost"], "·", "bar")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(text.contains("$4.20"), "formats cost as $X.XX: {text:?}");
}

#[test]
fn custom_value_labels_override_and_hide_defaults() {
    let mut spec = cspec(
        &["folder", "model", "ctx", "five", "seven", "cost"],
        "·",
        "percent",
    );
    for (segment, label) in spec
        .segments
        .iter_mut()
        .zip(["repo", "model", "context", "limit", "week", "spent"])
    {
        segment.label = Some(label.into());
    }
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        text.contains("repo proj"),
        "folder label prefixes its value: {text:?}"
    );
    assert!(
        text.contains("model Opus 4.8"),
        "model label prefixes its value: {text:?}"
    );
    assert!(
        text.contains("context ") && text.contains("72%"),
        "context label is replaced: {text:?}"
    );
    assert!(
        text.contains("limit ") && text.contains("34%"),
        "5-hour label is replaced: {text:?}"
    );
    assert!(
        text.contains("week ") && text.contains("62%"),
        "weekly label is replaced: {text:?}"
    );
    assert!(
        text.contains("spent $4.20"),
        "cost label prefixes its value: {text:?}"
    );

    let mut hidden = cspec(&["ctx", "five", "seven"], "·", "percent");
    for segment in &mut hidden.segments {
        segment.label = Some(String::new());
    }
    let text = run_script(&build_custom_statusline(&hidden).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        !text.contains("ctx") && !text.contains("5h") && !text.contains("7d"),
        "empty overrides hide labels: {text:?}"
    );
    assert!(
        text.contains("72%") && text.contains("34%") && text.contains("62%"),
        "values remain visible: {text:?}"
    );
}

#[test]
fn custom_text_segment_renders_verbatim_including_emoji() {
    let mut spec = cspec(&["folder"], "·", "bar");
    spec.segments.push(Segment {
        id: "text".into(),
        color: "default".into(),
        text: "🚀 dev".into(),
        label: None,
        icon: None,
    });
    spec.segments.push(Segment {
        id: "text".into(),
        color: "default".into(),
        text: "★".into(),
        label: None,
        icon: None,
    });
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(text.contains("🚀 dev"), "emoji text verbatim: {text:?}");
    assert!(text.contains('★'), "second text segment present: {text:?}");
}

#[test]
fn custom_color_wraps_segment_with_sgr() {
    let mut spec = cspec(&["folder"], "·", "bar");
    spec.segments[0].color = "red".into();
    let src = build_custom_statusline(&spec).unwrap();
    // The generated script carries the red SGR open (31) around the folder.
    assert!(
        src.contains("\\033[31m$cwd${RESET}"),
        "folder wrapped in red: {src}"
    );
    let text = run_script(&src, SAMPLE_PAYLOAD);
    assert!(
        text.contains("\u{1b}[31m"),
        "rendered output has the red escape: {text:?}"
    );
}

#[test]
fn claude_color_emits_brand_orange_truecolor() {
    // Claude's orange isn't in the ANSI 16, so it ships as a truecolor escape.
    let mut spec = cspec(&["model"], "·", "bar");
    spec.segments[0].color = "claude".into();
    let src = build_custom_statusline(&spec).unwrap();
    assert!(
        src.contains("\\033[38;2;217;119;87m"),
        "script carries claude truecolor: {src}"
    );
    let text = run_script(&src, SAMPLE_PAYLOAD);
    assert!(
        text.contains("\u{1b}[38;2;217;119;87m"),
        "rendered has the claude escape: {text:?}"
    );
    assert!(text.contains("Opus 4.8"), "model still shown: {text:?}");
}

#[test]
fn blue_color_matches_terminal_link_blue() {
    let mut spec = cspec(&["folder"], "·", "bar");
    spec.segments[0].color = "blue".into();
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        text.contains("\u{1b}[94mproj"),
        "bright blue emitted: {text:?}"
    );
}

#[test]
fn custom_meter_width_is_respected() {
    let mut wide = cspec(&["five"], "·", "bar");
    wide.meter_width = 14;
    let mut narrow = cspec(&["five"], "·", "bar");
    narrow.meter_width = 3;
    let count = |t: &str| t.chars().filter(|&c| c == '━' || c == '╸').count();
    let wide_bar = count(&run_script(
        &build_custom_statusline(&wide).unwrap(),
        SAMPLE_PAYLOAD,
    ));
    let narrow_bar = count(&run_script(
        &build_custom_statusline(&narrow).unwrap(),
        SAMPLE_PAYLOAD,
    ));
    assert!(
        wide_bar > narrow_bar,
        "wider meter draws more cells: {wide_bar} vs {narrow_bar}"
    );
}

#[test]
fn usage_meter_color_tracks_percentage() {
    let source = build_custom_statusline(&cspec(&["five"], "·", "bar")).unwrap();
    for (percentage, color) in [(49, "32"), (50, "33"), (79, "33"), (80, "31")] {
        let payload =
            format!(r#"{{"rate_limits":{{"five_hour":{{"used_percentage":{percentage}}}}}}}"#);
        let text = run_script(&source, &payload);
        assert!(
            text.contains(&format!("\u{1b}[{color}m")),
            "{percentage}% uses ANSI color {color}: {text:?}"
        );
    }
}

#[test]
fn blocks_meter_renders_block_glyphs() {
    let text = run_script(
        &build_custom_statusline(&cspec(&["five"], "·", "blocks")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(
        text.contains('▇') || text.contains('▁'),
        "block glyphs present: {text:?}"
    );
    assert!(text.contains("34%"), "still shows the percentage: {text:?}");
    assert!(
        !text.contains('━'),
        "blocks style draws no heavy-line bar: {text:?}"
    );
}

#[test]
fn added_meter_styles_render_their_glyphs() {
    // Sample payload: five_hour 34% at width 7 -> 2 full cells, partial cell
    // for the fractional styles, and a visible track.
    for (style, fill, track) in [
        ("segments", '▰', '▱'),
        ("squares", '■', '□'),
        ("shade", '▓', '░'),
        ("braille", '⣿', '⣀'),
    ] {
        let text = run_script(
            &build_custom_statusline(&cspec(&["five"], "·", style)).unwrap(),
            SAMPLE_PAYLOAD,
        );
        assert!(text.contains(fill), "{style} fill glyph present: {text:?}");
        assert!(
            text.contains(track),
            "{style} track glyph present: {text:?}"
        );
        assert!(
            text.contains("34%"),
            "{style} keeps the percentage: {text:?}"
        );
        assert!(
            !text.contains('━'),
            "{style} draws no heavy-line bar: {text:?}"
        );
    }
}

#[test]
fn shade_and_braille_meters_render_partial_cells() {
    // 34% of 7 leaves rem=38: shade shows its ▒ half-cell, braille its ⡆ step.
    let shade = run_script(
        &build_custom_statusline(&cspec(&["five"], "·", "shade")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(shade.contains('▒'), "shade partial cell present: {shade:?}");
    let braille = run_script(
        &build_custom_statusline(&cspec(&["five"], "·", "braille")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(
        braille.contains('⡆'),
        "braille partial cell present: {braille:?}"
    );
}

#[test]
fn dots_meter_renders_dot_glyphs() {
    let text = run_script(
        &build_custom_statusline(&cspec(&["five"], "·", "dots")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(text.contains('●'), "filled dots present: {text:?}");
    assert!(text.contains('○'), "empty dots present: {text:?}");
    assert!(text.contains("34%"), "still shows the percentage: {text:?}");
}

#[test]
fn icons_prefix_segments_when_enabled() {
    let mut spec = cspec(&["folder", "model", "five"], "·", "blocks");
    spec.icons = true;
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(text.contains("📁"), "folder icon: {text:?}");
    assert!(text.contains("✳"), "model icon: {text:?}");
    assert!(text.contains("⚡"), "5h icon: {text:?}");
    // Off by default, no glyphs leak in.
    let plain = run_script(
        &build_custom_statusline(&cspec(&["folder", "model"], "·", "bar")).unwrap(),
        SAMPLE_PAYLOAD,
    );
    assert!(!plain.contains("📁"), "no icon when disabled: {plain:?}");
}

#[test]
fn icons_use_their_own_segment_color() {
    let mut spec = cspec(&["folder", "model", "ctx", "five"], "·", "blocks");
    spec.icons = true;
    spec.segments[0].color = "blue".into();
    spec.segments[1].color = "claude".into();
    spec.segments[2].color = "magenta".into();
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        text.contains("\u{1b}[38;2;217;119;87m✳ Opus 4.8"),
        "model icon uses model color: {text:?}"
    );
    assert!(
        text.contains("\u{1b}[0m\u{1b}[2m⚡ 5h"),
        "5h icon resets before its own default style: {text:?}"
    );
    assert!(
        !text.contains("\u{1b}[94m✳"),
        "model icon does not inherit folder blue: {text:?}"
    );
    assert!(
        !text.contains("\u{1b}[35m⚡"),
        "5h icon does not inherit context magenta: {text:?}"
    );
}

#[test]
fn custom_icons_override_hide_and_respect_global_toggle() {
    let mut spec = cspec(&["folder", "text"], "·", "bar");
    spec.icons = true;
    spec.segments[0].icon = Some("⌂".into());
    spec.segments[1].text = "deploy".into();
    spec.segments[1].icon = Some("🚀".into());
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        text.contains("⌂ proj"),
        "folder override rendered: {text:?}"
    );
    assert!(text.contains("🚀 deploy"), "text icon rendered: {text:?}");
    assert!(!text.contains("📁"), "default icon replaced: {text:?}");

    spec.segments[0].icon = Some(String::new());
    let hidden = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        !hidden.contains('⌂') && !hidden.contains("📁"),
        "empty override hides icon: {hidden:?}"
    );

    spec.icons = false;
    let disabled = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        !disabled.contains("🚀"),
        "global toggle suppresses overrides: {disabled:?}"
    );
}

#[test]
fn vibrant_preset_matches_modern_variant() {
    let spec = preset_spec("vibrant").unwrap();
    assert_eq!(
        spec.segments
            .iter()
            .map(|segment| segment.id.as_str())
            .collect::<Vec<_>>(),
        ["folder", "model", "ctx", "five", "seven", "cost"]
    );
    assert_eq!(
        spec.segments
            .iter()
            .map(|segment| segment.color.as_str())
            .collect::<Vec<_>>(),
        ["blue", "claude", "default", "default", "default", "yellow"]
    );
    assert_eq!(spec.separator, "·");
    assert_eq!(spec.meter_style, "bar");
    assert_eq!(spec.meter_width, 7);
    assert!(spec.icons);
    assert!(!spec.git_status);
    let text = run_script(&build_custom_statusline(&spec).unwrap(), SAMPLE_PAYLOAD);
    assert!(
        text.contains("📁") && text.contains("✳") && text.contains("🧠"),
        "glyphs: {text:?}"
    );
    assert!(
        text.contains("⚡") && text.contains("📆") && text.contains("💰"),
        "usage glyphs: {text:?}"
    );
    assert!(text.contains("Opus 4.8"), "model shown: {text:?}");
    assert!(
        text.contains("\u{1b}[94m"),
        "folder carries the blue accent: {text:?}"
    );
    assert!(
        text.contains("\u{1b}[38;2;217;119;87m"),
        "model carries the Claude accent: {text:?}"
    );
    assert!(
        text.contains('━') || text.contains('╸'),
        "bar meter: {text:?}"
    );
    assert!(
        text.contains("34%") && text.contains("62%"),
        "both 5h and weekly render: {text:?}"
    );
    assert!(
        text.contains("\u{1b}[33m💰 $4.20"),
        "cost has its yellow accent: {text:?}"
    );
    assert!(!text.contains("🌿"), "modern has no branch: {text:?}");
}

fn make_dirty_repo() -> tempfile::TempDir {
    let td = tempfile::tempdir().unwrap();
    let p = td.path().to_str().unwrap().to_string();
    let git = |args: &[&str]| {
        std::process::Command::new("git")
            .args(["-C", &p])
            .args(args)
            .output()
            .unwrap();
    };
    git(&["init", "-q", "."]);
    git(&["symbolic-ref", "HEAD", "refs/heads/main"]);
    git(&["config", "user.email", "t@t"]);
    git(&["config", "user.name", "t"]);
    git(&["config", "commit.gpgsign", "false"]);
    std::fs::write(td.path().join("README.md"), "demo\n").unwrap();
    git(&["add", "README.md"]);
    git(&["commit", "-q", "-m", "init"]);
    std::fs::write(td.path().join("README.md"), "demo\nwip\n").unwrap();
    td
}

#[test]
fn git_status_marks_a_dirty_branch() {
    let repo = make_dirty_repo();
    let payload = serde_json::json!({
        "cwd": repo.path().to_str().unwrap(),
        "model": { "display_name": "X" }
    })
    .to_string();
    // Branch + git status is a builder capability (icons on to check the glyph).
    let mut spec = cspec(&["folder", "branch"], "·", "bar");
    spec.icons = true;
    spec.git_status = true;
    let text = run_script(&build_custom_statusline(&spec).unwrap(), &payload);
    assert!(text.contains("main"), "branch name shown: {text:?}");
    assert!(text.contains('✳'), "dirty marker shown: {text:?}");
    assert!(text.contains("🌿"), "branch icon shown: {text:?}");

    // A clean checkout drops the marker.
    std::process::Command::new("git")
        .args([
            "-C",
            repo.path().to_str().unwrap(),
            "checkout",
            "--",
            "README.md",
        ])
        .output()
        .unwrap();
    let clean = run_script(&build_custom_statusline(&spec).unwrap(), &payload);
    assert!(clean.contains("main"), "branch still shown: {clean:?}");
    assert!(!clean.contains('✳'), "clean tree has no marker: {clean:?}");
}

#[test]
fn custom_spec_accepts_legacy_string_form() {
    // Old custom.json stored segments as a plain array of ids.
    let legacy = r#"{"segments":["folder","model"],"separator":"·","meterStyle":"bar"}"#;
    let spec: CustomSpec = serde_json::from_str(legacy).unwrap();
    assert_eq!(spec.segments.len(), 2);
    assert_eq!(spec.segments[0].id, "folder");
    assert_eq!(spec.segments[0].color, "default");
    assert_eq!(spec.segments[0].label, None);
    assert_eq!(spec.segments[0].icon, None);
    assert_eq!(spec.meter_width, 7, "missing width defaults to 7");
    // And it builds a runnable script.
    assert!(build_custom_statusline(&spec).is_ok());
}

#[test]
fn custom_spec_validation_rejects_bad_specs() {
    let base = default_custom_spec();
    let empty = CustomSpec {
        segments: vec![],
        ..base.clone()
    };
    assert!(build_custom_statusline(&empty).is_err(), "empty rejected");
    let unknown = CustomSpec {
        segments: vec![seg("bogus")],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&unknown).is_err(),
        "unknown segment rejected"
    );
    let dup = CustomSpec {
        segments: vec![seg("folder"), seg("folder")],
        ..base.clone()
    };
    assert!(build_custom_statusline(&dup).is_err(), "duplicate rejected");
    let badcolor = CustomSpec {
        segments: vec![Segment {
            id: "folder".into(),
            color: "chartreuse".into(),
            text: String::new(),
            label: None,
            icon: None,
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&badcolor).is_err(),
        "unknown color rejected"
    );
    let emptytext = CustomSpec {
        segments: vec![Segment {
            id: "text".into(),
            color: "default".into(),
            text: "  ".into(),
            label: None,
            icon: None,
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&emptytext).is_err(),
        "empty text rejected"
    );
    let unsafetext = CustomSpec {
        segments: vec![Segment {
            id: "text".into(),
            color: "default".into(),
            text: "a$b".into(),
            label: None,
            icon: None,
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&unsafetext).is_err(),
        "unsafe text char rejected"
    );
    let unsafelabel = CustomSpec {
        segments: vec![Segment {
            label: Some("cost $".into()),
            ..seg("ctx")
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&unsafelabel).is_err(),
        "unsafe label rejected"
    );
    let unsafeicon = CustomSpec {
        segments: vec![Segment {
            icon: Some("$HOME".into()),
            ..seg("folder")
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&unsafeicon).is_err(),
        "unsafe icon rejected"
    );
    let longicon = CustomSpec {
        segments: vec![Segment {
            icon: Some("12345678901234567".into()),
            ..seg("folder")
        }],
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&longicon).is_err(),
        "over-long icon rejected"
    );
    let badsep = CustomSpec {
        separator: "".into(),
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&badsep).is_err(),
        "empty separator rejected"
    );
    let longsep = CustomSpec {
        separator: "abcd".into(),
        ..base.clone()
    };
    assert!(
        build_custom_statusline(&longsep).is_err(),
        "over-long separator rejected"
    );
    let badstyle = CustomSpec {
        meter_style: "wild".into(),
        ..base
    };
    assert!(
        build_custom_statusline(&badstyle).is_err(),
        "unknown meter style rejected"
    );
}

#[test]
fn custom_allows_multiple_text_segments() {
    let mut spec = cspec(&["folder"], "·", "bar");
    spec.segments.push(Segment {
        id: "text".into(),
        color: "default".into(),
        text: "a".into(),
        label: None,
        icon: None,
    });
    spec.segments.push(Segment {
        id: "text".into(),
        color: "default".into(),
        text: "b".into(),
        label: None,
        icon: None,
    });
    assert!(
        build_custom_statusline(&spec).is_ok(),
        "duplicate text ids are allowed"
    );
}

#[test]
fn custom_spec_persists_and_round_trips_through_state() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, "{}").unwrap();
    let mut spec = cspec(&["folder", "model", "seven"], "›", "percent");
    spec.segments[0].color = "blue".into();
    spec.segments[1].color = "cyan".into();
    spec.segments[1].icon = Some("🤖".into());
    spec.segments[2].label = Some("week".into());
    spec.meter_width = 10;
    assert!(!claude_statusline_state_at(&settings, &sldir).has_saved_custom);
    apply_custom_at(&settings, &sldir, &spec).unwrap();

    let state = claude_statusline_state_at(&settings, &sldir);
    assert_eq!(state.selected, "custom");
    assert_eq!(state.custom, spec, "state echoes the saved spec");
    assert!(state.has_saved_custom);
    assert!(sldir.join("custom.json").exists());
}

#[test]
fn custom_defaults_to_full_emoji_bar_with_neutral_folder() {
    let spec = default_custom_spec();
    assert_eq!(
        spec.segments
            .iter()
            .map(|segment| segment.id.as_str())
            .collect::<Vec<_>>(),
        ["folder", "model", "ctx", "five", "seven", "cost"]
    );
    assert_eq!(spec.segments[0].color, "default");
    assert_eq!(spec.segments[1].color, "claude");
    assert_eq!(spec.segments.last().unwrap().color, "yellow");
    assert_eq!(spec.separator, "·");
    assert_eq!(spec.meter_style, "bar");
    assert_eq!(spec.meter_width, 7);
    assert!(spec.icons);
    assert!(!spec.git_status);
}

#[test]
fn switching_custom_preset_custom_keeps_spec_and_wrapper() {
    let (_td, settings, sldir) = slt_dirs();
    // Forwarder enabled over a user line.
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh"}}"#,
    )
    .unwrap();
    install_claude_statusline_at(&settings).unwrap();

    let spec = cspec(&["folder", "cost"], "/", "bar");
    apply_custom_at(&settings, &sldir, &spec).unwrap();
    apply_claude_statusline_at(&settings, &sldir, "minimal").unwrap();
    // Preset apply must not touch the saved custom spec.
    assert_eq!(
        read_custom_spec(&sldir),
        spec,
        "custom spec survives a preset"
    );

    // Re-select Custom via the generic dispatcher -> restores the saved spec.
    apply_claude_statusline_at(&settings, &sldir, "custom").unwrap();
    let v = read_json(&settings);
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(cmd.contains("agent_limits"), "forwarder preserved: {cmd}");
    assert!(
        cmd.contains("lpm-custom.sh"),
        "custom chained underneath: {cmd}"
    );
    let embedded = unwrap_statusline(cmd).unwrap();
    assert_eq!(
        detect_template_id(&embedded, &sldir).as_deref(),
        Some("custom")
    );
}

#[test]
fn preview_current_renders_prior_line_and_empty_when_none() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, "{}").unwrap();
    // No prior line -> empty preview.
    let empty =
        preview_claude_statusline_at(&json!({"kind":"current"}), &sldir, &settings).unwrap();
    assert!(empty.is_empty(), "no prior line -> empty: {empty:?}");
    // Snapshot a user's own command; "current" previews it.
    std::fs::create_dir_all(&sldir).unwrap();
    std::fs::write(
        sldir.join("original.json"),
        r#"{"type":"command","command":"printf 'hi %s' \"$(cat | jq -r .model.display_name)\""}"#,
    )
    .unwrap();
    let out =
        preview_claude_statusline_at(&json!({"kind":"current"}), &sldir, &settings).unwrap();
    assert!(
        out.contains("Opus 4.8"),
        "previews the user's own line: {out:?}"
    );
}

#[test]
fn preview_meters_returns_ansi() {
    let (_td, settings, sldir) = slt_dirs();
    let out = preview_claude_statusline_at(
        &json!({"kind":"template","id":"meters"}),
        &sldir,
        &settings,
    )
    .unwrap();
    assert!(
        out.contains("Opus 4.8") && out.contains("34%"),
        "renders fields: {out:?}"
    );
    assert!(out.contains('\u{1b}'), "includes ANSI escapes: {out:?}");
}

#[test]
fn preview_unknown_kind_errors() {
    let (_td, settings, sldir) = slt_dirs();
    assert!(preview_claude_statusline_at(&json!({"kind":"bogus"}), &sldir, &settings).is_err());
}

#[test]
fn finalize_ai_saves_validates_and_applies() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, "{}").unwrap();
    let good = "#!/bin/sh\ni=$(cat); printf 'model %s' \"$(printf '%s' \"$i\" | jq -r .model.display_name)\"\n";
    let sel = json!({"kind":"current"});
    let script =
        finalize_ai_statusline_at(good, "show the model", &sel, &settings, &sldir).unwrap();
    assert!(script.contains("jq -r .model.display_name"));
    assert!(sldir.join("lpm-ai.sh").exists() && sldir.join("ai.json").exists());
    // ai.json records both the description and the base selection.
    let meta: Value =
        serde_json::from_slice(&std::fs::read(sldir.join("ai.json")).unwrap()).unwrap();
    assert_eq!(meta["description"], "show the model");
    assert_eq!(meta["baseSelection"]["kind"], "current");
    let state = claude_statusline_state_at(&settings, &sldir);
    assert_eq!(state.selected, "ai");
    assert_eq!(state.ai_description, "show the model");
    // A script that prints nothing is rejected with a useful error.
    let bad = "#!/bin/sh\ncat >/dev/null\n";
    assert!(finalize_ai_statusline_at(bad, "x", &sel, &settings, &sldir).is_err());
}

#[test]
fn ai_base_from_template_and_referenced_file_and_none() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, "{}").unwrap();

    // Base from a template spec: the generated script body.
    let base =
        ai_base_for_selection_at(&json!({"kind":"template","id":"meters"}), &sldir, &settings);
    let base = base.expect("template has a base");
    assert!(
        base.contains("5h") && base.contains("meter"),
        "template base is the script: {base:?}"
    );

    // Base from a "current" line that is just `sh '<file>'`: the file's content.
    std::fs::create_dir_all(&sldir).unwrap();
    let userscript = sldir.join("mine.sh");
    std::fs::write(&userscript, "#!/bin/sh\necho MINE\n").unwrap();
    let original = json!({
        "type": "command",
        "command": format!("sh '{}'", userscript.display()),
    });
    std::fs::write(sldir.join("original.json"), original.to_string()).unwrap();
    let base = ai_base_for_selection_at(&json!({"kind":"current"}), &sldir, &settings);
    assert_eq!(
        base.as_deref(),
        Some("#!/bin/sh\necho MINE\n"),
        "reads the referenced file"
    );

    // No base: "current" with nothing configured (fresh dir).
    let (_td2, settings2, sldir2) = slt_dirs();
    std::fs::write(&settings2, "{}").unwrap();
    assert!(
        ai_base_for_selection_at(&json!({"kind":"current"}), &sldir2, &settings2).is_none(),
        "no prior line -> generate from scratch"
    );
}

#[test]
fn ai_prompt_includes_base_only_when_present() {
    let with = ai_statusline_prompt("make it red", Some("#!/bin/sh\necho x"));
    assert!(with.contains("current status line script") && with.contains("echo x"));
    let without = ai_statusline_prompt("from scratch", None);
    assert!(!without.contains("current status line script"));
    assert!(without.contains("from scratch"));
}

#[test]
fn strip_code_fences_unwraps_markdown() {
    let raw = "```sh\n#!/bin/sh\necho hi\n```";
    assert_eq!(strip_code_fences(raw), "#!/bin/sh\necho hi");
    assert_eq!(strip_code_fences("plain\nline"), "plain\nline");
}

#[test]
fn apply_template_without_wrapper_writes_template_object() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, r#"{"model":"opus"}"#).unwrap();
    apply_claude_statusline_at(&settings, &sldir, "minimal").unwrap();

    let v = read_json(&settings);
    assert_eq!(v["model"], "opus", "siblings preserved");
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(
        cmd.contains("lpm-minimal.sh"),
        "points at the template script: {cmd}"
    );
    assert!(
        !cmd.contains("agent_limits"),
        "no forwarder when limits are off"
    );
    assert!(sldir.join("lpm-minimal.sh").exists(), "script written");
}

#[test]
fn apply_template_with_wrapper_preserves_forwarder_and_chains_template() {
    let (_td, settings, sldir) = slt_dirs();
    // Start with the usage-limit forwarder installed over a user line.
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh"}}"#,
    )
    .unwrap();
    install_claude_statusline_at(&settings).unwrap();

    apply_claude_statusline_at(&settings, &sldir, "meters").unwrap();
    let v = read_json(&settings);
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(
        cmd.contains("agent_limits"),
        "forwarder stays chained: {cmd}"
    );
    assert!(
        cmd.contains("lpm-meters.sh"),
        "template chained underneath: {cmd}"
    );
    // The embedded original is the template, not the forwarder itself.
    let embedded = unwrap_statusline(cmd).unwrap();
    assert!(detect_template_id(&embedded, &sldir).as_deref() == Some("meters"));
}

#[test]
fn switching_templates_does_not_nest() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh"}}"#,
    )
    .unwrap();
    install_claude_statusline_at(&settings).unwrap();

    apply_claude_statusline_at(&settings, &sldir, "minimal").unwrap();
    apply_claude_statusline_at(&settings, &sldir, "context").unwrap();
    let v = read_json(&settings);
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(
        !cmd.contains("lpm-minimal.sh"),
        "old template gone, no nesting: {cmd}"
    );
    assert!(
        cmd.contains("lpm-context.sh"),
        "new template present: {cmd}"
    );
    assert!(cmd.contains("agent_limits"), "forwarder still on top");
    // Exactly one status-line marker -> the wrapper is not doubly nested.
    assert_eq!(
        cmd.matches(STATUSLINE_MARKER).count(),
        1,
        "single wrapper: {cmd}"
    );
}

#[test]
fn restore_returns_exact_snapshot_and_snapshots_once() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh","padding":2}}"#,
    )
    .unwrap();

    apply_claude_statusline_at(&settings, &sldir, "minimal").unwrap();
    let snap_after_first = std::fs::read(sldir.join("original.json")).unwrap();
    // Switching to another template must NOT re-snapshot our own template.
    apply_claude_statusline_at(&settings, &sldir, "context").unwrap();
    assert_eq!(
        std::fs::read(sldir.join("original.json")).unwrap(),
        snap_after_first,
        "snapshot taken only once"
    );

    apply_claude_statusline_at(&settings, &sldir, "current").unwrap();
    let v = read_json(&settings);
    assert_eq!(
        v["statusLine"]["command"], "my-line.sh",
        "restores the exact original"
    );
    assert_eq!(v["statusLine"]["padding"], 2);
}

#[test]
fn restore_null_snapshot_removes_line_but_keeps_forwarder() {
    let (_td, settings, sldir) = slt_dirs();
    // No prior status line, but usage-limit forwarder is enabled.
    std::fs::write(&settings, "{}").unwrap();
    install_claude_statusline_at(&settings).unwrap();

    apply_claude_statusline_at(&settings, &sldir, "meters").unwrap();
    apply_claude_statusline_at(&settings, &sldir, "current").unwrap();
    let v = read_json(&settings);
    let cmd = v["statusLine"]["command"].as_str().unwrap();
    assert!(cmd.contains("agent_limits"), "forwarder kept: {cmd}");
    let embedded = unwrap_statusline(cmd).unwrap();
    assert!(
        embedded.is_null(),
        "underlying line cleared back to none: {embedded}"
    );
}

#[test]
fn state_reports_selected_template_and_custom() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh"}}"#,
    )
    .unwrap();

    let s0 = claude_statusline_state_at(&settings, &sldir);
    assert_eq!(s0.selected, "current");
    assert!(s0.has_custom, "a live non-lpm line counts as custom");

    apply_claude_statusline_at(&settings, &sldir, "context").unwrap();
    let s1 = claude_statusline_state_at(&settings, &sldir);
    assert_eq!(s1.selected, "context");
    assert!(
        s1.has_custom,
        "snapshot exists -> My status line has something to restore"
    );
}

#[test]
fn startup_refresh_rewrites_only_the_active_builtin_script() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(
        &settings,
        r#"{"statusLine":{"type":"command","command":"my-line.sh"}}"#,
    )
    .unwrap();
    install_claude_statusline_at(&settings).unwrap();
    apply_claude_statusline_at(&settings, &sldir, "meters").unwrap();
    let meters_path = sldir.join("lpm-meters.sh");
    std::fs::write(&meters_path, "#!/bin/sh\necho legacy\n").unwrap();
    let settings_before = std::fs::read(&settings).unwrap();

    refresh_active_template_at(&settings, &sldir).unwrap();

    let expected = build_custom_statusline(&preset_spec("meters").unwrap()).unwrap();
    assert_eq!(std::fs::read_to_string(&meters_path).unwrap(), expected);
    assert_eq!(std::fs::read(&settings).unwrap(), settings_before);

    let mut custom = default_custom_spec();
    custom.segments[0].color = "blue".into();
    apply_custom_at(&settings, &sldir, &custom).unwrap();
    let custom_script = std::fs::read(sldir.join("lpm-custom.sh")).unwrap();
    let custom_spec = std::fs::read(sldir.join("custom.json")).unwrap();

    refresh_active_template_at(&settings, &sldir).unwrap();

    assert_eq!(
        std::fs::read(sldir.join("lpm-custom.sh")).unwrap(),
        custom_script
    );
    assert_eq!(
        std::fs::read(sldir.join("custom.json")).unwrap(),
        custom_spec
    );
}

#[test]
fn state_no_line_no_snapshot_is_not_custom() {
    let (_td, settings, sldir) = slt_dirs();
    std::fs::write(&settings, "{}").unwrap();
    let s = claude_statusline_state_at(&settings, &sldir);
    assert_eq!(s.selected, "current");
    assert!(!s.has_custom, "nothing configured -> nothing to restore");
}
