"""A2UI generation orchestrator for AGenUI Studio.

This module wires together the reused benchmark building blocks (prompt building,
JSON extraction, A2UI validation) with a BYOK provider, and emits a stream of
``GenerationEvent`` objects that the server forwards to the browser over SSE.

Reused (read-only, NOT modified) from ``test/a2ui_benchmark``:
    - generation/prompt_builder.py : build_system_prompt / build_user_prompt
    - generation/extractor.py      : extract_json_blocks / parse_json_pair
    - validation/validator.py      : validate_payloads

Generation loop (see plan Part B5):
    building_prompt -> calling_model (stream tokens) -> extracting -> validating
    -> saving -> done
"""

from __future__ import annotations

import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Generator

from .benchmark.generation.extractor import (
    extract_json_blocks,
    parse_json_pair,
)
from .benchmark.generation.prompt_builder import (
    build_system_prompt,
    build_user_prompt,
)
from .benchmark.validation.validator import validate_payloads

from . import storage
from .providers import OpenAICompatProvider, ProviderError


# Repo root / skills / a2ui-generation (shared read-only with the benchmark).
SKILL_DIR = Path(__file__).resolve().parents[3] / "skills" / "a2ui-generation"


# --- A2UI compliance auto-fix ---------------------------------------------
# Best-effort fixups so generated protocols pass validate_a2ui.py without
# manual edits. The authoritative whitelist lives in
# skills/a2ui-generation/scripts/validate_a2ui.py; we import it so the two
# never drift apart, and fall back to a hardcoded copy if the import fails.
_SKILL_SCRIPTS = SKILL_DIR / "scripts"
if str(_SKILL_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_SKILL_SCRIPTS))
try:  # pragma: no cover - the real dependency is always present locally
    from validate_a2ui import (  # type: ignore[import-not-found]
        ALLOWED_COMMON_STYLE_KEYS,
        ALLOWED_TEXT_STYLE_KEYS,
        ALLOWED_ICON_NAMES,
    )
except Exception:  # noqa: BLE001 - fallback only, keeps the module importable
    ALLOWED_COMMON_STYLE_KEYS = {
        "width", "height", "padding", "padding-inline-start", "padding-left",
        "padding-inline-end", "padding-right", "padding-block-start",
        "padding-top", "padding-block-end", "padding-bottom", "margin",
        "margin-inline-start", "margin-left", "margin-inline-end",
        "margin-right", "margin-block-start", "margin-top", "margin-block-end",
        "margin-bottom", "background", "background-color", "background-image",
        "border-radius", "border-color", "border-style", "border-width",
        "opacity", "overflow", "display", "visibility", "flex-grow",
        "flex-shrink", "flex-wrap", "justify-content", "align-items",
        "align-content", "align-self", "aspect-ratio", "filter", "box-shadow",
    }
    ALLOWED_TEXT_STYLE_KEYS = {
        "color", "font-size", "font-weight", "font-family", "line-height",
        "text-align", "line-clamp", "text-overflow", "text-decoration",
        "text-decoration-line", "text-decoration-style",
        "text-decoration-color", "text-decoration-thickness",
    }
    ALLOWED_ICON_NAMES = {
        "accountCircle", "add", "arrowBack", "arrowForward", "attachFile",
        "calendarToday", "call", "camera", "check", "close", "delete",
        "download", "edit", "error", "event", "favorite", "favoriteOff",
        "folder", "help", "home", "info", "locationOn", "lock", "lockOpen",
        "mail", "menu", "moreHoriz", "moreVert", "notifications",
        "notificationsOff", "payment", "person", "phone", "photo", "print",
        "refresh", "search", "send", "settings", "share", "shoppingCart",
        "star", "starHalf", "starOff", "upload", "visibility",
        "visibilityOff", "warning",
    }

# Map common invalid (e.g. Material weather) icon names to an allowed one.
_ICON_FALLBACK_MAP = {
    "wbsunny": "favorite",
    "wb_sunny": "favorite",
    "wbcloudy": "info",
    "sunny": "favorite",
    "cloud": "info",
    "cloudy": "info",
    "rainy": "info",
    "thunderstorm": "warning",
    "snow": "info",
}
_ICON_FALLBACK_DEFAULT = "info"


def _autofix_components(
    comp_dict: dict | None, data_dict: dict | None = None
) -> dict | None:
    """Mutate a parsed protocol in place to satisfy A2UI validation rules.

    Returns the same dict (or None when the input is unusable). The cleaned
    payload is what gets saved and rendered, so the Studio UI never shows a
    spurious "validation failed" for mistakes the validator can fix itself.

    Fixes applied (mirroring skills/a2ui-generation/scripts/validate_a2ui.py):
      * root must stay a transparent canvas -> drop solid background fills
      * only Text/RichText may use text-only style keys (e.g. `color`)
      * unknown style keys (e.g. `gap`) -> drop them (not in the whitelist)
      * Icon `name` not in the allowed set -> replace with a valid fallback
      * padding/margin shorthand -> expand to 4 values
      * border-radius/border-width -> collapse to single px value
      * binding path outside dataModel root -> prepend root prefix
    """
    if not isinstance(comp_dict, dict):
        return comp_dict
    # Resolve the dataModel root path for binding-path fixup.
    data_root = ""
    if isinstance(data_dict, dict):
        dm = data_dict.get("updateDataModel", {})
        if isinstance(dm, dict):
            rp = dm.get("path")
            if isinstance(rp, str) and rp:
                data_root = rp

    components = comp_dict.get("updateComponents", {}).get("components")
    if not isinstance(components, list):
        return comp_dict
    for comp in components:
        if not isinstance(comp, dict):
            continue
        cid = comp.get("id")
        ctype = comp.get("component")
        styles = comp.get("styles")
        if not isinstance(styles, dict):
            styles = {}
            comp["styles"] = styles

        if cid == "root":
            # root is a transparent canvas; solid fills and text-only keys
            # are forbidden, and unknown style keys are not allowed either.
            for k in {"background-color", "background", "background-image"}:
                styles.pop(k, None)
            for k in [k for k in styles if k in ALLOWED_TEXT_STYLE_KEYS]:
                styles.pop(k, None)
            for k in [k for k in styles if k not in ALLOWED_COMMON_STYLE_KEYS]:
                styles.pop(k, None)
            continue

        if ctype == "Icon":
            name = comp.get("name")
            if isinstance(name, str) and name not in ALLOWED_ICON_NAMES:
                comp["name"] = _ICON_FALLBACK_MAP.get(
                    name.strip().lower(), _ICON_FALLBACK_DEFAULT
                )
            # fall through to the non-Text style cleanup below

        if ctype in ("Text", "RichText"):
            allowed = ALLOWED_COMMON_STYLE_KEYS | ALLOWED_TEXT_STYLE_KEYS
        else:
            allowed = ALLOWED_COMMON_STYLE_KEYS
            # text-only style keys belong on Text components only.
            for k in [k for k in styles if k in ALLOWED_TEXT_STYLE_KEYS]:
                styles.pop(k, None)
        # drop any unknown style key (e.g. `gap`).
        for k in [k for k in styles if k not in allowed]:
            styles.pop(k, None)
        # expand padding/margin shorthand to 4 values (validator requires 4).
        for sk in ("padding", "margin"):
            sv = styles.get(sk)
            if isinstance(sv, str):
                parts = sv.split()
                if len(parts) == 1:
                    styles[sk] = f"{parts[0]} {parts[0]} {parts[0]} {parts[0]}"
                elif len(parts) == 2:
                    styles[sk] = f"{parts[0]} {parts[1]} {parts[0]} {parts[1]}"
                elif len(parts) == 3:
                    styles[sk] = f"{parts[0]} {parts[1]} {parts[2]} {parts[1]}"
        # collapse border-radius/border-width to single px (validator requires 1).
        for sk in ("border-radius", "border-width"):
            sv = styles.get(sk)
            if isinstance(sv, str):
                parts = sv.split()
                if len(parts) > 1:
                    styles[sk] = parts[0]
        # fix binding paths outside dataModel root (validator line 223-227).
        if data_root and data_root != "/":
            _fix_binding_paths(comp, data_root)
    return comp_dict


def _fix_binding_paths(node, data_root: str) -> None:
    """Recursively fix absolute binding paths that fall outside data_root."""
    if isinstance(node, dict):
        for key, val in list(node.items()):
            if key == "path" and isinstance(val, str):
                if val.startswith("/") and val != data_root and not val.startswith(f"{data_root}/"):
                    node[key] = f"{data_root}{val}"
            else:
                _fix_binding_paths(val, data_root)
    elif isinstance(node, list):
        for item in node:
            _fix_binding_paths(item, data_root)


# Instruction wrapped around the user message on refinement turns (i.e. when a
# conversation history carrying a previous protocol is present). Without it the
# model treats a follow-up as a brand-new request and regenerates from scratch,
# producing a result that diverges heavily from the original protocol. New
# conversations have no history, so their prompt is sent through untouched.
REFINEMENT_INSTRUCTION = """\
The conversation above contains a previously generated A2UI protocol: the
assistant's most recent message holds it as two JSON code blocks (the first is
updateComponents, the second is updateDataModel).

The user now wants to refine that existing protocol. You MUST:
1. Treat the previous protocol as the baseline. Keep its structure, components,
   data bindings, content and styling UNCHANGED, except for the specific
   modifications requested below.
2. Apply ONLY the changes described in the user's request. Do not redesign,
   re-layout, or rewrite unrelated parts.
3. Output the COMPLETE updated protocol - both the full updateComponents block
   and the full updateDataModel block - not just the changed fragments.

User's refinement request:
{user_request}"""


@dataclass
class GenerationEvent:
    """A single SSE event pushed from the server to the browser.

    type is one of: "stage" | "token" | "reasoning" | "done" | "error".

    ``token`` carries the final-answer text (the A2UI JSON); ``reasoning``
    carries a reasoning model's chain-of-thought (display-only, streamed long
    before the answer so the UI can show live "thinking" progress).
    """

    type: str
    data: dict[str, Any] = field(default_factory=dict)


def _stage(name: str, **extra: Any) -> GenerationEvent:
    return GenerationEvent(type="stage", data={"stage": name, **extra})


def _split_combined_payload(
    comp_dict: dict | None, data_dict: dict | None
) -> tuple[dict | None, dict | None]:
    """Split a single combined payload back into the expected pair.

    On refinement turns some models collapse the two required blocks into one
    object shaped like {"updateComponents": ..., "updateDataModel": ...} (often
    echoing the format they saw in the chat history). The two-block extractor
    yields that object as ``comp_dict`` with ``data_dict`` still None; recover
    by splitting it so the turn still succeeds.
    """
    if data_dict is not None or not isinstance(comp_dict, dict):
        return comp_dict, data_dict
    if "updateComponents" in comp_dict and "updateDataModel" in comp_dict:
        version = comp_dict.get("version", "v0.9")
        return (
            {"version": version, "updateComponents": comp_dict["updateComponents"]},
            {"version": version, "updateDataModel": comp_dict["updateDataModel"]},
        )
    return comp_dict, data_dict


def _attempt(full_text: str) -> dict[str, Any]:
    """Run extract -> parse -> validate over a raw model response.

    Returns a dict with keys: components, datamodel, validation, error, raw.
    ``components``/``datamodel`` are None when extraction/parsing failed.
    """
    comp_json, data_json = extract_json_blocks(full_text)
    comp_dict, data_dict, parse_error = parse_json_pair(comp_json, data_json)

    # Recover from a combined single-object response (see helper docstring).
    comp_dict, data_dict = _split_combined_payload(comp_dict, data_dict)
    if comp_dict is not None and data_dict is not None:
        parse_error = None

    if comp_dict is None or data_dict is None:
        return {
            "components": None,
            "datamodel": None,
            "validation": None,
            "error": parse_error or "Failed to extract A2UI JSON from model output",
            "raw": full_text,
        }

    validation = validate_payloads(comp_dict, data_dict)
    comp_dict = _autofix_components(comp_dict, data_dict)
    validation = validate_payloads(comp_dict, data_dict)
    return {
        "components": comp_dict,
        "datamodel": data_dict,
        "validation": validation,
        "error": None,
        "raw": full_text,
    }


def _has_payload(result: dict[str, Any]) -> bool:
    return result.get("components") is not None and result.get("datamodel") is not None


def generate_a2ui_stream(
    provider: OpenAICompatProvider,
    user_prompt: str,
    mode: str = "component",
    enable_reasoning: bool | None = None,
    history: list[dict] | None = None,
) -> Generator[GenerationEvent, None, None]:
    """Generate an A2UI protocol, yielding progress events as they happen.

    Tokens are streamed to the caller as the model produces them. After the
    stream completes, the response is extracted and validated. A parseable
    result is always saved and returned (with its validation report); only a
    total extraction failure yields an ``error`` event. No automatic retry is
    performed.

    ``enable_reasoning`` is forwarded to the provider to force the model's
    thinking switch on/off (``None`` keeps the model default).

    ``history`` is an optional list of prior chat messages (user/assistant
    dicts) enabling multi-turn refinement of a protocol.
    """
    try:
        yield _stage("building_prompt")
        is_page = mode == "page"
        system_prompt = build_system_prompt(
            SKILL_DIR,
            is_page=is_page,
            allow_placeholder_images=True,
        )
        user_message = build_user_prompt(user_prompt)
        # On refinement turns, explicitly frame the request as an incremental
        # modification of the previous protocol so the model preserves the rest
        # (a bare follow-up prompt would otherwise be treated as a fresh
        # generation and diverge heavily from the original).
        if history:
            user_message = REFINEMENT_INSTRUCTION.format(user_request=user_message)

        yield _stage("calling_model", model=provider.model)
        full_text = ""
        for tok in provider.chat_stream(
            system_prompt, user_message, enable_reasoning=enable_reasoning,
            history=history,
        ):
            if tok.kind == "reasoning":
                # Chain-of-thought: display-only, does not enter the payload.
                yield GenerationEvent(type="reasoning", data={"content": tok.text})
            else:
                full_text += tok.text
                yield GenerationEvent(type="token", data={"content": tok.text})

        yield _stage("extracting")
        yield _stage("validating")
        result = _attempt(full_text)

        if not _has_payload(result):
            yield GenerationEvent(
                type="error",
                data={
                    "message": (
                        "Failed to extract a valid A2UI protocol from the model "
                        "output. Please refine your prompt and try again."
                    ),
                    "code": "extraction_failed",
                    "raw_response": result.get("raw", ""),
                },
            )
            return

        yield _stage("saving")
        record = storage.save_protocol(
            prompt=user_prompt,
            mode=mode,
            provider=provider.name,
            model=provider.model,
            components_dict=result["components"],
            datamodel_dict=result["datamodel"],
        )

        validation = result.get("validation") or {}
        yield GenerationEvent(
            type="done",
            data={
                "success": True,
                "protocol_id": record["id"],
                "protocol_url": f"/api/protocols/{record['id']}/raw",
                "components": result["components"],
                "datamodel": result["datamodel"],
                "validation_passed": validation.get("validation_passed", False),
                "validation_errors": validation.get("validation_errors", []),
                "validation_warnings": validation.get("validation_warnings", []),
            },
        )

    except ProviderError as exc:
        yield GenerationEvent(
            type="error",
            data={
                "message": exc.message,
                "code": exc.code,
                "status_code": exc.status_code,
                "detail": exc.detail,
            },
        )
    except FileNotFoundError as exc:
        yield GenerationEvent(
            type="error",
            data={"message": f"Skill resources missing: {exc}", "code": "config"},
        )
    except Exception as exc:  # noqa: BLE001 - surface any unexpected failure
        yield GenerationEvent(
            type="error",
            data={"message": f"Unexpected error: {exc}", "code": "internal"},
        )


def generate_a2ui_sync(
    provider: OpenAICompatProvider,
    user_prompt: str,
    mode: str = "component",
    enable_reasoning: bool | None = None,
    history: list[dict] | None = None,
) -> dict[str, Any]:
    """Non-streaming wrapper (for curl / testing). Returns the final event data."""
    final: dict[str, Any] = {
        "success": False,
        "message": "Generation produced no result",
        "code": "internal",
    }
    for event in generate_a2ui_stream(provider, user_prompt, mode, enable_reasoning, history):
        if event.type in ("done", "error"):
            final = event.data
    return final
