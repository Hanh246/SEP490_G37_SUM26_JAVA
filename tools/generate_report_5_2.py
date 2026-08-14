"""Generate Report 5.2 (Integration Tests) from the integration api test folder.

Reads every *IT.java file under src/test/java/com/sep/comiverse/integration/api,
extracts one row per @Test, and joins the execution result from the latest
maven-surefire XML reports. Output is a TSV file that can be pasted straight
into the Report 5.2 spreadsheet.

Columns: Test Case ID | Endpoint (Method + URL) | UC-ID / SCR-ID | Scenario Name |
Test Type | Coverage Technique | Role / Session | Validation Direction |
Given | When | Then | Priority | Status | Defect ID | Notes
"""
import html
import re
import xml.etree.ElementTree as ET
from pathlib import Path

API_DIR = Path("src/test/java/com/sep/comiverse/integration/api")
SUREFIRE_DIR = Path("target/surefire-reports")
OUT_FILE = Path("report_5_2_integration_tests.tsv")

HEADER = [
    "Test Case ID", "Endpoint (Method + URL)", "UC-ID / SCR-ID", "Scenario Name",
    "Test Type", "Coverage Technique", "Role / Session", "Validation Direction",
    "Given (Precondition + Seed Data)", "When (Request / Action)", "Then (Expected Result)",
    "Priority", "Status", "Defect ID", "Notes",
]

# Class-level seed data, transcribed from each @BeforeEach block.
SEED = {
    "AdminControllerIT": "6 users seeded: ADMIN admin_users_it, MODERATOR, READER, READER target account, READER banned (INACTIVE), READER soft-deleted; roles ADMIN/MODERATOR/READER/PROJECT_LEADER/TRANSLATOR exist",
    "AdminPayoutControllerIT": "ADMIN, READER, AUTHOR with ready Stripe account acct_it_ready, AUTHOR with incomplete onboarding, TRANSLATOR without payout account; StripeGatewayService stubbed to return transfer tr_sandbox_it",
    "AdminReportControllerIT": "ADMIN, MODERATOR, PROJECT_LEADER, READER reporter; 2 report categories (MODERATOR -> COMIC/CHAPTER, PROJECT_LEADER -> CHAPTER_TRANSLATIONS); 1 PUBLISHED comic as report target",
    "AdminSubscriptionControllerIT": "ADMIN, READER, paying READER; startup-seeded MONTHLY subscription plan",
    "AdminSystemSettingsControllerIT": "ADMIN, READER, MODERATOR; startup-seeded MONTHLY and YEARLY premium plans",
    "AppealControllerIT": "AUTHOR owner, second AUTHOR, USER reader, MODERATOR, ADMIN; 1 comic in NEEDS_CHANGES that was mod-edited and not yet appealed",
    "AuthControllerIT": "ACTIVE READER testuser_ctrl@example.com with BCrypt password Password123!",
    "AuthorChapterControllerIT": "ACTIVE AUTHOR author_comic@example.com; comics/chapters created per test through helpers",
    "AuthorComicAnalyticsControllerIT": "ACTIVE AUTHOR author_analytics_ctrl owning 1 DRAFT comic",
    "AuthorComicControllerIT": "ACTIVE AUTHOR author_comic_ctrl owning 1 DRAFT comic; ACTIVE READER reader_author_ctrl",
    "AuthorDashboardControllerIT": "ACTIVE AUTHOR author_dash_ctrl with no comics",
    "AuthorProfileControllerIT": "ACTIVE AUTHOR author_profile_ctrl",
    "BannedKeywordControllerIT": "ACTIVE ADMIN admin_banned_kw; banned keyword table empty",
    "BroadcastControllerIT": "ACTIVE ADMIN admin_broadcast",
    "ChapterControllerIT": "ACTIVE ADMIN and ACTIVE READER; 1 PUBLISHED comic with chapter 1 in SUBMITTED_FOR_REVIEW",
    "ChapterTranslationControllerIT": "ACTIVE TRANSLATOR translator_chap_ctrl",
    "ChatControllerIT": "ACTIVE READER reader_chat_ctrl; Redis replaced by mocks",
    "ChatFlagControllerIT": "ACTIVE MODERATOR mod_chat_flag",
    "ComicControllerIT": "ACTIVE READER reader_comic_ctrl; 1 PUBLISHED comic 'Sample Test Comic Control'",
    "CommentControllerIT": "ACTIVE READER reader_comment_ctrl; 1 PUBLISHED comic authored by that reader",
    "CreatorPayoutControllerIT": "ACTIVE AUTHOR author_payout_ctrl with no Stripe payout account",
    "EmailTestControllerIT": "No seed data",
    "ForumCommentControllerIT": "ACTIVE READER reader_forum_comment; 1 forum thread authored by that reader",
    "ForumThreadControllerIT": "ACTIVE READER reader_forum_thread; forum empty",
    "GenreControllerIT": "No seed data; genre catalogue as created by the schema",
    "GlossaryControllerIT": "ACTIVE TRANSLATOR translator_glossary",
    "GoogleAuthControllerIT": "No seed data; no Google credentials configured",
    "NotificationControllerIT": "ACTIVE READER reader_notification with no notifications",
    "OfflineDownloadControllerIT": "ACTIVE READER reader_offline with no offline downloads",
    "PageControllerIT": "ACTIVE READER reader_page_ctrl",
    "PremiumPlanControllerIT": "No seed data; startup-seeded premium plans only",
    "ProjectTeamControllerIT": "ACTIVE READER reader_proj_team with no team membership",
    "ReadingHistoryControllerIT": "ACTIVE READER reader_history with empty reading history",
    "ReportCategoryControllerIT": "ACTIVE READER reader_report_cat; ReportCategoryService stubbed to return an empty category list",
    "ReportControllerIT": "ACTIVE READER reader_report",
    "ReviewControllerIT": "ACTIVE READER reader_review_ctrl",
    "StripeWebhookControllerIT": "No seed data; StripeGatewayService mocked, no webhook signing secret configured",
    "SubmissionControllerIT": "ACTIVE MODERATOR mod_submission with an empty submission queue",
    "SubscriptionControllerIT": "ACTIVE READER reader_sub with no active subscription",
    "SyncControllerIT": "ACTIVE ADMIN admin_sync",
    "TeamWorkspaceControllerIT": "ACTIVE PROJECT_LEADER leader_workspace",
    "TranslationPoolControllerIT": "ACTIVE TRANSLATOR translator_pool",
    "TranslatorRegistrationControllerIT": "ACTIVE READER reader_translator_reg with no translator application",
    "UploadControllerIT": "No seed data; CloudinaryService mocked",
    "UserControllerIT": "ACTIVE READER reader_user_ctrl",
    "UserLikeControllerIT": "ACTIVE READER reader_like_ctrl with no likes",
    "UserRatingControllerIT": "ACTIVE READER reader_rating_ctrl with no ratings",
    "UserSaveControllerIT": "ACTIVE READER reader_save_ctrl with no saved comics",
    "WebSocketChatControllerIT": "No seed data",
}

STATUS_CODES = {
    "isOk": "200 OK", "isCreated": "201 Created", "isAccepted": "202 Accepted",
    "isNoContent": "204 No Content", "isFound": "302 Found", "isBadRequest": "400 Bad Request",
    "isUnauthorized": "401 Unauthorized", "isPaymentRequired": "402 Payment Required",
    "isForbidden": "403 Forbidden", "isNotFound": "404 Not Found",
    "isMethodNotAllowed": "405 Method Not Allowed", "isNotAcceptable": "406 Not Acceptable",
    "isConflict": "409 Conflict", "isGone": "410 Gone", "isPreconditionFailed": "412 Precondition Failed",
    "isUnsupportedMediaType": "415 Unsupported Media Type",
    "isUnprocessableEntity": "422 Unprocessable Entity", "isTooManyRequests": "429 Too Many Requests",
    "isInternalServerError": "500 Internal Server Error", "isNotImplemented": "501 Not Implemented",
    "isBadGateway": "502 Bad Gateway", "isServiceUnavailable": "503 Service Unavailable",
    "is2xxSuccessful": "2xx Success", "is3xxRedirection": "3xx Redirection",
    "is4xxClientError": "4xx Client Error", "is5xxServerError": "5xx Server Error",
}

ROLE_LABELS = [
    ("admin", "admin_01 (ADMIN)"),
    ("moderator", "moderator_01 (MODERATOR)"),
    ("leader", "leader_01 (PROJECT_LEADER)"),
    ("author", "author_01 (AUTHOR)"),
    ("translator", "translator_01 (TRANSLATOR)"),
    ("reader", "reader_01 (READER)"),
    ("reporter", "reporter_01 (READER)"),
    ("user", "user_01 (USER)"),
    ("mod", "moderator_01 (MODERATOR)"),
    ("payer", "payer_01 (READER)"),
    ("owner", "owner_01"),
]

DISPLAY_RE = re.compile(r'@DisplayName\(\s*"((?:[^"\\]|\\.)*)"\s*\)')
ID_RE = re.compile(r"^(TC-INT-[A-Za-z0-9]+-\w+):\s*(.*)$")
ENDPOINT_RE = re.compile(r"^(GET|POST|PUT|PATCH|DELETE)\s+(\S+)\s+-\s+(.*)$")
CONST_RE = re.compile(r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"')
METHOD_RE = re.compile(r"\bvoid\s+(\w+)\s*\(")
VERB_RE = re.compile(r"\b(get|post|put|patch|delete|head|options|multipart)\s*\(")
STATUS_RE = re.compile(r"status\(\)\.(\w+)\(")
TOKEN_RE = re.compile(r'header\(\s*(?:"Authorization"|HttpHeaders\.AUTHORIZATION)\s*,\s*"Bearer\s*"\s*\+\s*([A-Za-z0-9_.]+(?:\([A-Za-z0-9_.]*\))?)')
LOCAL_TOKEN_RE = re.compile(r"String\s+(\w+)\s*=\s*jwtTokenUtil\.generate\w*Token\(\s*([A-Za-z0-9_.()]+)")


def balanced(text: str, open_idx: int) -> tuple[str, int]:
    """Return the content of the (...) group starting at open_idx and the index after it."""
    depth = 0
    in_str = False
    escape = False
    for i in range(open_idx, len(text)):
        c = text[i]
        if in_str:
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[open_idx + 1:i], i + 1
    return text[open_idx + 1:], len(text)


def split_args(args: str) -> list[str]:
    parts, depth, cur, in_str, escape = [], 0, [], False, False
    for c in args:
        if in_str:
            cur.append(c)
            if escape:
                escape = False
            elif c == "\\":
                escape = True
            elif c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
            cur.append(c)
        elif c in "([{":
            depth += 1
            cur.append(c)
        elif c in ")]}":
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            parts.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
    if "".join(cur).strip():
        parts.append("".join(cur).strip())
    return parts


def unquote(literal: str) -> str:
    return literal.strip()[1:-1].replace('\\"', '"').replace("\\n", " ").replace("\\\\", "\\")


def is_literal(expr: str) -> bool:
    e = expr.strip()
    return e.startswith('"') and e.endswith('"') and len(e) >= 2


def render_url(expr: str, consts: dict) -> str:
    """Turn a Java URL expression into a readable path."""
    out = []
    for token in re.split(r"\+(?![^\"]*\"[^\"]*$)", expr):
        t = token.strip()
        if not t:
            continue
        if is_literal(t):
            out.append(unquote(t))
        elif t in consts:
            out.append(consts[t])
        elif "randomUUID" in t:
            out.append("{nonExistentId}")
        elif re.search(r"\.getId\(\)", t):
            out.append("{id}")
        else:
            out.append("{" + re.sub(r"[^A-Za-z0-9]", "", t.split(".")[0])[:24] + "}")
    return "".join(out)


def collect_params(stmt: str, consts: dict) -> str:
    pairs = []
    for m in re.finditer(r"\.(?:param|queryParam)\(", stmt):
        args, _ = balanced(stmt, m.end() - 1)
        parts = split_args(args)
        if len(parts) < 2:
            continue
        key = unquote(parts[0]) if is_literal(parts[0]) else parts[0]
        vals = []
        for p in parts[1:]:
            if is_literal(p):
                vals.append(unquote(p))
            elif p in consts:
                vals.append(consts[p])
            elif "randomUUID" in p:
                vals.append("<nonExistentId>")
            else:
                vals.append("<" + p.split("(")[0].split(".")[-1] + ">")
        pairs.append(f"{key}={','.join(vals)}")
    return "&".join(pairs)


def collect_body(stmt: str) -> str:
    for m in re.finditer(r"\.content\(", stmt):
        args, _ = balanced(stmt, m.end() - 1)
        args = args.strip()
        if is_literal(args):
            body = unquote(args)
        elif "writeValueAsString" in args or "toString" in args or "Body(" in args:
            inner = args
            body = "serialized DTO: " + re.sub(r"\s+", " ", inner)[:90]
        else:
            body = "body from " + re.sub(r"\s+", " ", args)[:80]
        body = re.sub(r"\s+", " ", body).strip()
        return body[:170] + ("..." if len(body) > 170 else "")
    return ""


def readable_java(text: str) -> str:
    """Turn leftover Java accessor expressions into readable placeholders."""
    text = re.sub(r"\b(\w+)\.getId\(\)\.toString\(\)", r"<\1 id>", text)
    text = re.sub(r"\b(\w+)\.getId\(\)", r"<\1 id>", text)
    text = re.sub(r"\b(\w+)\.get(\w+)\(\)", lambda m: f"<{m.group(1)} {m.group(2)[0].lower() + m.group(2)[1:]}>", text)
    text = re.sub(r"\bUUID\.randomUUID\(\)(?:\.toString\(\))?", "<nonExistentId>", text)
    text = re.sub(r"\b(\w+)\.name\(\)", r"<\1>", text)
    return text


def render_matcher(path: str, matcher: str) -> str:
    m = re.sub(r"\s+", " ", matcher).strip()
    if not m:
        return path + " exists"
    for pattern, repl in (
        (r"^is\((.*)\)$", r"= \1"),
        (r"^equalTo\((.*)\)$", r"= \1"),
        (r"^hasSize\((.*)\)$", r"has \1 item(s)"),
        (r"^notNullValue\(\)$", "is not null"),
        (r"^nullValue\(\)$", "is null"),
        (r"^empty\(\)$", "is empty"),
        (r"^emptyIterable\(\)$", "is empty"),
        (r"^contains\((.*)\)$", r"contains exactly [\1]"),
        (r"^containsInAnyOrder\((.*)\)$", r"contains [\1]"),
        (r"^hasItem\((.*)\)$", r"contains \1"),
        (r"^hasItems\((.*)\)$", r"contains \1"),
        (r"^everyItem\(is\((.*)\)\)$", r"every item = \1"),
        (r"^everyItem\((.*)\)$", r"every item \1"),
        (r"^not\(hasItem\((.*)\)\)$", r"does not contain \1"),
        (r"^not\((.*)\)$", r"is not \1"),
        (r"^greaterThan\((.*)\)$", r"> \1"),
        (r"^greaterThanOrEqualTo\((.*)\)$", r">= \1"),
        (r"^lessThan\((.*)\)$", r"< \1"),
        (r"^containsString\((.*)\)$", r"contains text \1"),
        (r"^startsWith\((.*)\)$", r"starts with \1"),
        (r"^anyOf\((.*)\)$", r"matches any of [\1]"),
        (r"^instanceOf\((.*)\)$", r"is a \1"),
    ):
        new = re.sub(pattern, repl, m)
        if new != m:
            return readable_java(f"{path} {new}".replace('"', "'"))
    return readable_java(f"{path} {m}".replace('"', "'"))


def collect_assertions(body: str, consts: dict) -> list[str]:
    out = []
    for m in re.finditer(r"jsonPath\(", body):
        args, _ = balanced(body, m.end() - 1)
        parts = split_args(args)
        if not parts:
            continue
        path = unquote(parts[0]) if is_literal(parts[0]) else parts[0]
        matcher = parts[1] if len(parts) > 1 else ""
        out.append(render_matcher(path, matcher))
    for m in re.finditer(r"\.andExpect\(\s*header\(\)\.(\w+)\(", body):
        out.append(f"response header {m.group(1)}")
    if re.search(r"content\(\)\.string\(", body):
        out.append("raw response body asserted")
    return out


def humanise_arrange(source: str) -> str:
    """Condense the arrange section of a test into a few readable statements."""
    text = re.sub(r"//[^\n]*", " ", source)
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"\s+", " ", text)
    statements = []
    for raw in text.split(";"):
        s = raw.strip().strip("{}").strip()
        if not s or "jwtTokenUtil.generate" in s:
            continue
        s = re.sub(r"^(?:final\s+)?(?:String|var|UUID|int|long|Instant|LocalDate|LocalDateTime|BigDecimal|"
                   r"ObjectNode|Map<[^>]*>|List<[^>]*>|[A-Z]\w*)\s+(\w+)\s*=\s*", r"\1 = ", s)
        if len(s) > 110:
            s = s[:110] + "..."
        statements.append(s)
    return "; ".join(statements[:3]) + (" ..." if len(statements) > 3 else "")


def load_surefire() -> dict:
    results = {}
    if not SUREFIRE_DIR.exists():
        return results
    for xml in SUREFIRE_DIR.glob("TEST-*.xml"):
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        for tc in root.iter("testcase"):
            cls = (tc.get("classname") or "").split(".")[-1]
            name = tc.get("name") or ""
            name = re.sub(r"\(.*\)$", "", name)
            verdict, detail = "Pass", ""
            node = tc.find("failure") if tc.find("failure") is not None else tc.find("error")
            if node is not None:
                verdict = "Fail"
                detail = re.sub(r"\s+", " ", node.get("message") or "").strip()[:180]
            elif tc.find("skipped") is not None:
                verdict = "Skipped"
            results[(cls, name)] = (verdict, detail)
    return results


TECHNIQUE_RULES = [
    ("State Transition", r"\b(already|transition|retr(y|ies|ied|yable)|idempotent|restored?|unban\w*|"
                         r"revok\w*|deactivat\w*|reactivat\w*|escalat\w*)\b"),
    ("Error Guessing", r"\b(not found|unknown|non-?existent|does not exist|unmapped|no longer|"
                       r"fail(s|ed|ure|ing)?|error|unexpected|crash\w*|without a\b)\b"),
    ("Boundary Value Analysis", r"\b(above|below|exceed(s|ing)?|maximum|minimum|too long|too many|too short|"
                               r"zero|negative|clamp(ed|s)?|boundary|limit|decimals?|out of range)\b"),
    ("Equivalence Partitioning", r"\b(malformed|invalid|blank|missing|empty|validation|required|unsupported|"
                                 r"duplicate|mismatch|normali[sz]ed?|case-insensitive|soft-deleted|excluded?|trim(med)?)\b"),
    ("State Transition", r"\b(approv\w*|reject\w*|pay|paid|payment|ban|banned|publish\w*|resolv\w*|"
                         r"submit\w*|cancel\w*|reset|pending|conflict|status)\b"),
    ("Decision Table", r"\b(filter\w*|search\w*|sort\w*|order\w*|newest|oldest|scoped?|route[sd]?)\b"),
    ("Equivalence Partitioning", r"\b(pagination|paginated|page|default(s)?)\b"),
]


def technique(scenario: str, codes: list[str], endpoint: str) -> str:
    s = scenario.lower()
    if any(c.startswith(("401", "403")) for c in codes):
        return "Branch / Condition Coverage"
    for name, pattern in TECHNIQUE_RULES:
        if re.search(pattern, s):
            return name
    return "Use Case Testing"


def priority(codes: list[str], endpoint: str) -> str:
    if any(c.startswith(("401", "403")) for c in codes):
        return "Critical"
    if any(c.startswith(("2", "3")) for c in codes):
        return "High"
    if any(c.startswith("5") or c.startswith("502") for c in codes):
        return "High"
    return "Medium"


# Token variables whose name does not reflect the seeded role.
SESSION_OVERRIDES = {
    ("AuthControllerIT", "testUser"): "reader_01 (READER)",
    ("AppealControllerIT", "readerUser"): "reader_01 (role USER)",
}


def session_label(var: str) -> str:
    v = var.lower()
    for key, label in ROLE_LABELS:
        if key in v:
            return label
    return var


def main() -> None:
    surefire = load_surefire()
    rows = []
    missing_status = 0

    for f in sorted(API_DIR.glob("*IT.java")):
        text = f.read_text(encoding="utf-8")
        cls = f.stem
        consts = dict(CONST_RE.findall(text))
        seed = SEED.get(cls, "")

        segments = text.split("@Test")[1:]
        for seg in segments:
            dm = DISPLAY_RE.search(seg)
            mm = METHOD_RE.search(seg)
            if not mm:
                continue
            method = mm.group(1)
            body_start = seg.find("{", mm.end())
            body = seg[body_start:] if body_start >= 0 else seg
            # cut the body at the closing brace of the method
            depth = 0
            for i, c in enumerate(body):
                if c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        body = body[:i]
                        break

            display = dm.group(1) if dm else method
            display = html.unescape(display.replace('\\"', '"'))
            idm = ID_RE.match(display)
            case_id = idm.group(1) if idm else f"TC-INT-{cls.replace('IT', '')}-{method}"
            rest = idm.group(2) if idm else display
            em = ENDPOINT_RE.match(rest)
            if em:
                endpoint = f"{em.group(1)} {em.group(2)}"
                scenario = em.group(3)
            else:
                endpoint = ""
                scenario = rest

            inner = body[1:] if body.startswith("{") else body
            perform_idx = [m.start() for m in re.finditer(r"mockMvc\s*\.\s*perform\(", body)]
            arrange_src = body[1:perform_idx[0]] if perform_idx else inner
            arrange = humanise_arrange(arrange_src)

            when_parts = []
            unknown_target = False
            for k, idx in enumerate(perform_idx):
                open_paren = body.find("(", body.find("perform", idx))
                stmt, _ = balanced(body, open_paren)
                vm = VERB_RE.search(stmt)
                if not vm:
                    continue
                verb = vm.group(1).upper()
                verb_args, _ = balanced(stmt, vm.end() - 1)
                url_expr = split_args(verb_args)[0] if split_args(verb_args) else '""'
                url = render_url(url_expr, consts)
                if verb == "MULTIPART":
                    verb = "POST (multipart)"
                if "randomUUID" in verb_args:
                    unknown_target = True
                qs = collect_params(stmt, consts)
                token_m = TOKEN_RE.search(stmt)
                req = f"{verb} {url}" + (f"?{qs}" if qs else "")
                bd = collect_body(stmt)
                if bd:
                    req += f" | body: {bd}"
                if ".file(" in stmt:
                    req += " | multipart file part"
                if not token_m and "Authorization" not in stmt:
                    req += " | no Authorization header"
                when_parts.append(req)

            if not endpoint and when_parts:
                endpoint = " ".join(when_parts[0].split(" ")[:2])

            when = when_parts[-1] if when_parts else "(no HTTP call)"
            if len(when_parts) > 1:
                when = f"Setup call(s): {' ; '.join(when_parts[:-1])} -- then: {when}"
            when = readable_java(when)

            # role / session
            tokens = TOKEN_RE.findall(body)
            locals_map = dict((k, v) for k, v in LOCAL_TOKEN_RE.findall(body))
            if tokens:
                var = tokens[-1]
                var = locals_map.get(var, var).strip("().")
                if "invalidToken" in var:
                    role = "invalid token"
                else:
                    role = SESSION_OVERRIDES.get((cls, var), session_label(var))
            elif "Authorization" in body:
                role = "malformed / non-bearer token"
            else:
                role = "(no session)"

            codes = []
            for name in STATUS_RE.findall(body):
                codes.append(STATUS_CODES.get(name, name))
            for m in re.finditer(r"status\(\)\.is\(\s*(\d{3})", body):
                codes.append(m.group(1))
            codes = list(dict.fromkeys(codes))

            assertions = collect_assertions(body, consts)
            then_bits = [f"HTTP {c}" for c in codes] or ["request completed"]
            then_bits += assertions[:5]
            if len(assertions) > 5:
                then_bits.append(f"(+{len(assertions) - 5} more response assertions)")
            db_asserts = len(re.findall(r"assertThat\(", body))
            if db_asserts:
                then_bits.append(f"persisted state verified with {db_asserts} repository assertion(s)")
            if re.search(r"\bverify\(", body):
                then_bits.append("collaborator interaction verified")
            then = "; ".join(then_bits)

            given = seed
            extra = []
            if unknown_target or "{nonExistentId}" in when:
                extra.append("requested identifier does not exist")
            if "not-a-uuid" in when or "invalid-uuid" in when:
                extra.append("identifier is not a valid UUID")
            if arrange:
                extra.append(f"in-test arrange: {readable_java(arrange)}")
            if extra:
                given = f"{given}; {'; '.join(extra)}" if given else "; ".join(extra)

            test_type = "Security" if any(c.startswith(("401", "403")) for c in codes) else "Integration/API"
            if any(p.startswith("$.errors") for p in assertions):
                direction = "Client -> Server (Bean Validation)"
            elif db_asserts:
                direction = "Server -> DB"
            elif assertions:
                direction = "Server -> Client"
            else:
                direction = "N/A"

            status, failure = surefire.get((cls, method), ("", ""))
            if not status:
                missing_status += 1
                status = "Not executed"

            notes = f"Source: {cls}#{method}"
            if failure:
                notes += f"; Observed failure: {failure}"
            if "stripeGatewayService" in body or "StripeGatewayService" in body:
                notes += "; Stripe gateway stubbed"
            if "MockBean" in text and "given(" in body:
                notes += "; collaborator stubbed in-test"

            rows.append([
                case_id, endpoint, "", scenario, test_type,
                technique(scenario, codes, endpoint), role, direction,
                given, when, then, priority(codes, endpoint), status, "", notes,
            ])

    def clean(v: str) -> str:
        return re.sub(r"[\t\r\n]+", " ", v).strip()

    with OUT_FILE.open("w", encoding="utf-8-sig", newline="") as fh:
        fh.write("\t".join(HEADER) + "\n")
        for r in rows:
            fh.write("\t".join(clean(c) for c in r) + "\n")

    print(f"rows={len(rows)}  file={OUT_FILE}")
    print(f"not-executed rows={missing_status}")
    from collections import Counter
    print("status:", Counter(r[12] for r in rows))
    print("type  :", Counter(r[4] for r in rows))
    print("techn :", Counter(r[5] for r in rows))
    print("prio  :", Counter(r[11] for r in rows))
    print("role  :", Counter(r[6] for r in rows))
    blank_endpoint = [r[0] for r in rows if not r[1]]
    if blank_endpoint:
        print("rows without endpoint:", blank_endpoint[:20])


if __name__ == "__main__":
    main()
