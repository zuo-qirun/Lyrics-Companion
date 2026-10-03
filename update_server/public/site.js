/* Shared release rendering for the public website. No framework or remote scripts required. */
(function (root, factory) {
    "use strict";
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    if (root && root.document) {
        if (root.document.readyState === "loading") {
            root.document.addEventListener("DOMContentLoaded", () =>
                api.mount(root.document, root),
            );
        } else api.mount(root.document, root);
    }
})(typeof window !== "undefined" ? window : null, function () {
    "use strict";
    const repository = "https://github.com/zuo-qirun/Lyrics-Companion";
    function safeUrl(value) {
        if (typeof value !== "string" || !value.trim()) return "";
        try {
            const url = new URL(
                value,
                "https://lyrics-companion.zuoqirun.top/",
            );
            return ["https:", "http:"].includes(url.protocol)
                ? value.trim()
                : "";
        } catch (_) {
            return "";
        }
    }
    function releaseChannel(release) {
        if (release.channel === "beta" || release.prerelease === true)
            return "beta";
        if (!release.channel || release.channel === "stable") return "stable";
        return "unknown";
    }
    function releasePage(release) {
        if (safeUrl(release.releaseUrl)) return release.releaseUrl;
        if (typeof release.releaseTag === "string" && release.releaseTag) {
            return (
                repository +
                "/releases/tag/" +
                encodeURIComponent(release.releaseTag)
            );
        }
        return "";
    }
    function releaseNotes(release) {
        if (Array.isArray(release.changelog)) {
            return release.changelog
                .filter((item) => typeof item === "string" && item.trim())
                .slice(0, 60);
        }
        const raw =
            typeof release.changelogText === "string"
                ? release.changelogText
                : typeof release.changelog === "string"
                  ? release.changelog
                  : "";
        return raw
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter((line) => line && !/^#{1,6}\s/.test(line))
            .slice(0, 60);
    }
    function normalizeRelease(release, requestedChannel) {
        if (!release || typeof release !== "object" || Array.isArray(release))
            throw new Error("Invalid release");
        const channel = releaseChannel(release);
        if (
            channel === "unknown" ||
            (requestedChannel === "stable" && channel !== "stable")
        ) {
            throw new Error("Unexpected release channel");
        }
        const available =
            Number(release.versionCode) > 0 && release.betaAvailable !== false;
        const url = available ? safeUrl(release.apkUrl) : "";
        const kind = !available
            ? "unavailable"
            : requestedChannel === "beta" && channel === "stable"
              ? "fallback"
              : "release";
        return {
            channel,
            kind,
            available,
            downloadable: Boolean(url),
            url,
            version: available
                ? String(release.versionName || "版本 " + release.versionCode)
                : "暂无可用版本",
            size: Number(release.size),
            source:
                release.downloadChannel === "github" ? "GitHub" : "官方镜像",
            date:
                release.builtAt ||
                release.publishedAt ||
                release.syncedAt ||
                "",
            dateLabel:
                release.builtAt || release.publishedAt ? "发布于" : "同步于",
            page: available ? releasePage(release) : "",
            notes: available ? releaseNotes(release) : [],
        };
    }
    function formatSize(bytes) {
        return Number.isFinite(bytes) && bytes > 0
            ? (bytes / 1024 / 1024).toFixed(1) + " MB"
            : "";
    }
    function formatDate(value) {
        const date = new Date(value);
        if (!value || !Number.isFinite(date.getTime())) return "";
        return new Intl.DateTimeFormat("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            timeZone: "Asia/Shanghai",
        }).format(date);
    }
    function filterHistory(versions, channel) {
        return versions
            .filter(
                (release) =>
                    release &&
                    typeof release === "object" &&
                    releaseChannel(release) !== "unknown" &&
                    (channel === "all" || releaseChannel(release) === channel),
            )
            .slice()
            .sort(
                (a, b) =>
                    (Number(b.versionCode) || 0) - (Number(a.versionCode) || 0),
            );
    }
    function plainText(value) {
        return value.replace(/\*\*/g, "").replace(/`/g, "");
    }
    function appendNotes(document, container, notes) {
        container.replaceChildren();
        if (!notes.length) {
            const paragraph = document.createElement("p");
            paragraph.textContent = "该版本暂未提供更新说明。";
            container.append(paragraph);
            return;
        }
        let list = null;
        for (const note of notes) {
            const text = note.trim().replace(/^[-*]\s+/, "");
            const notice = text.startsWith(">") || /^⚠|^这是测试版/.test(text);
            if (notice) {
                list = null;
                const quote = document.createElement("blockquote");
                quote.textContent = plainText(text.replace(/^>\s*/, ""));
                container.append(quote);
            } else {
                if (!list) {
                    list = document.createElement("ul");
                    container.append(list);
                }
                const item = document.createElement("li");
                item.textContent = plainText(text);
                list.append(item);
            }
        }
    }
    async function fetchJson(window, url) {
        const controller = new window.AbortController();
        const timer = window.setTimeout(() => controller.abort(), 10000);
        try {
            const response = await window.fetch(url, {
                cache: "no-store",
                signal: controller.signal,
            });
            if (!response.ok) throw new Error("Release request failed");
            return await response.json();
        } finally {
            window.clearTimeout(timer);
        }
    }
    function mount(document, window) {
        document.querySelectorAll("[data-preview-style]").forEach((button) => {
            button.addEventListener("click", () => {
                const stage = document.querySelector("[data-style-preview]");
                stage.className =
                    "style-preview " + button.dataset.previewStyle;
                document
                    .querySelectorAll("[data-preview-style]")
                    .forEach((choice) => {
                        choice.setAttribute(
                            "aria-pressed",
                            String(choice === button),
                        );
                    });
            });
        });
        if (document.querySelector("[data-release-channel]"))
            mountChannels(document, window);
        if (document.getElementById("archive-list"))
            mountArchive(document, window);
    }
    function mountChannels(document, window) {
        for (const card of document.querySelectorAll(
            "[data-release-channel]",
        )) {
            const channel = card.dataset.releaseChannel;
            const node = (name) =>
                card.querySelector("[data-field='" + name + "']");
            let generation = 0;
            const load = async () => {
                const request = ++generation;
                node("retry").disabled = true;
                node("retry").hidden = true;
                node("download").hidden = true;
                node("github").hidden = true;
                node("notice").hidden = true;
                node("changelog").hidden = true;
                node("version").textContent = "正在读取版本…";
                node("detail").textContent = "";
                node("status").textContent = "正在连接发布服务…";
                try {
                    const model = normalizeRelease(
                        await fetchJson(
                            window,
                            channel === "beta"
                                ? "/update-beta.json"
                                : "/update.json",
                        ),
                        channel,
                    );
                    if (request !== generation) return;
                    node("version").textContent = model.version;
                    const date = formatDate(model.date);
                    node("detail").textContent = [
                        formatSize(model.size),
                        date ? model.dateLabel + " " + date : "",
                        model.downloadable ? "下载来源：" + model.source : "",
                    ]
                        .filter(Boolean)
                        .join(" · ");
                    if (model.kind === "fallback") {
                        node("notice").textContent =
                            "当前测试通道提供正式版；暂无比它更新的测试构建。";
                        node("notice").hidden = false;
                    } else if (model.kind === "unavailable") {
                        node("notice").textContent =
                            channel === "beta"
                                ? "目前暂无测试构建，可以先使用正式版。"
                                : "目前暂无可用正式版，请稍后再试。";
                        node("notice").hidden = false;
                    }
                    node("status").textContent = model.downloadable
                        ? ""
                        : model.available
                          ? "APK 暂未准备就绪，可前往 GitHub 发布页查看。"
                          : "";
                    if (model.downloadable) {
                        node("download").href = model.url;
                        node("download").textContent =
                            channel === "stable"
                                ? "下载正式版 APK"
                                : model.kind === "fallback"
                                  ? "下载当前正式版 APK"
                                  : "下载测试版 APK";
                        node("download").hidden = false;
                    }
                    if (model.page) {
                        node("github").href = model.page;
                        node("github").hidden = false;
                    }
                    if (model.available) {
                        node("changelog").querySelector("summary").textContent =
                            model.kind === "fallback"
                                ? "当前正式版更新说明"
                                : channel === "beta"
                                  ? "测试版更新说明"
                                  : "正式版更新说明";
                        appendNotes(document, node("notes"), model.notes);
                        node("changelog").hidden = false;
                    }
                } catch (_) {
                    if (request !== generation) return;
                    node("version").textContent = "版本信息暂不可用";
                    node("status").textContent =
                        "连接失败，请重试或从版本归档下载。";
                    node("retry").hidden = false;
                } finally {
                    if (request === generation) node("retry").disabled = false;
                }
            };
            node("retry").addEventListener("click", load);
            load();
        }
    }
    function mountArchive(document, window) {
        const list = document.getElementById("archive-list");
        const count = document.getElementById("archive-count");
        const retry = document.getElementById("archive-retry");
        const buttons = Array.from(
            document.querySelectorAll("[data-filter-channel]"),
        );
        let channel =
            new URL(window.location.href).searchParams.get("channel") ||
            "stable";
        if (!["stable", "beta", "all"].includes(channel)) channel = "stable";
        let versions = [],
            ready = false;
        const render = () => {
            buttons.forEach((button) =>
                button.setAttribute(
                    "aria-pressed",
                    String(button.dataset.filterChannel === channel),
                ),
            );
            if (!ready) return;
            list.replaceChildren();
            const selected = filterHistory(versions, channel);
            count.textContent = selected.length + " 个版本";
            if (!selected.length) {
                const empty = document.createElement("p");
                empty.className = "empty-state";
                empty.textContent =
                    "暂无" +
                    (channel === "beta"
                        ? "测试版"
                        : channel === "stable"
                          ? "正式版"
                          : "") +
                    "版本记录。";
                list.append(empty);
                return;
            }
            for (const release of selected) {
                const actual = releaseChannel(release);
                const item = document.createElement("article");
                item.className = "archive-item";
                const meta = document.createElement("div");
                meta.className = "archive-meta";
                const badge = document.createElement("span");
                badge.className =
                    "badge" + (actual === "beta" ? " archive-beta" : "");
                badge.textContent =
                    actual === "beta" ? "测试版 · BETA" : "正式版 · STABLE";
                const title = document.createElement("h2");
                title.textContent = String(release.versionName || "未命名版本");
                const detail = document.createElement("p");
                detail.textContent = [
                    formatDate(release.publishedAt || release.builtAt),
                    formatSize(Number(release.size)),
                    "构建 " + (release.versionCode || "—"),
                ]
                    .filter(Boolean)
                    .join(" · ");
                meta.append(badge, title, detail);
                const content = document.createElement("div");
                content.className = "archive-content";
                const notes = document.createElement("div");
                notes.className = "release-notes";
                appendNotes(document, notes, releaseNotes(release));
                const actions = document.createElement("div");
                actions.className = "actions";
                const url = safeUrl(release.apkUrl);
                if (url) {
                    const link = document.createElement("a");
                    link.className =
                        "button down " +
                        (actual === "beta" ? "beta-button" : "primary");
                    link.href = url;
                    link.textContent =
                        "下载" +
                        (actual === "beta" ? "测试版" : "正式版") +
                        " APK";
                    const source = document.createElement("span");
                    source.className = "release-detail";
                    source.textContent =
                        "下载来源：" +
                        (release.downloadChannel === "github"
                            ? "GitHub"
                            : "官方镜像");
                    actions.append(link, source);
                } else {
                    const unavailable = document.createElement("span");
                    unavailable.className = "release-detail";
                    unavailable.textContent = "APK 暂不可用";
                    actions.append(unavailable);
                }
                const page = releasePage(release);
                if (page) {
                    const github = document.createElement("a");
                    github.className = "text-link";
                    github.textContent = "GitHub 发布页 ↗";
                    github.href = page;
                    actions.append(github);
                }
                content.append(notes, actions);
                item.append(meta, content);
                list.append(item);
            }
        };
        buttons.forEach((button) =>
            button.addEventListener("click", () => {
                channel = button.dataset.filterChannel;
                const url = new URL(window.location.href);
                url.searchParams.set("channel", channel);
                window.history.replaceState(null, "", url);
                render();
            }),
        );
        const load = async () => {
            retry.hidden = true;
            retry.disabled = true;
            count.textContent = "正在读取…";
            try {
                const data = await fetchJson(window, "/versions.json");
                if (!data || !Array.isArray(data.versions))
                    throw new Error("Invalid archive");
                versions = data.versions;
                ready = true;
                render();
            } catch (_) {
                ready = false;
                count.textContent = "连接失败";
                list.replaceChildren();
                const error = document.createElement("p");
                error.className = "empty-state error-state";
                error.textContent =
                    "版本归档暂时无法读取，请重试或前往 GitHub 查看发布。";
                list.append(error);
                retry.hidden = false;
            } finally {
                retry.disabled = false;
            }
        };
        retry.addEventListener("click", load);
        render();
        load();
    }
    return {
        safeUrl,
        releaseChannel,
        releasePage,
        releaseNotes,
        normalizeRelease,
        formatSize,
        formatDate,
        filterHistory,
        mount,
    };
});
