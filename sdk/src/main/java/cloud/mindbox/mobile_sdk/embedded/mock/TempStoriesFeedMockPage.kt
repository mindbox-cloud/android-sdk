package cloud.mindbox.mobile_sdk.embedded.mock

// MUST NOT REACH `develop`: stands in for the real page URL. Ported from the iOS mock so the
// page contract stays identical across platforms.
internal object TempStoriesFeedMockPage {

    fun html(scenario: TempMindboxStoriesFeedMock.Scenario): String =
        PAGE_TEMPLATE.replace("__SCENARIO__", scenario.name)

    private val PAGE_TEMPLATE = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
    <style>
      * { -webkit-tap-highlight-color: transparent; }
      html, body { margin: 0; padding: 0; background: transparent; }
      body {
        font-family: -apple-system, system-ui, sans-serif;
        -webkit-user-select: none;
        user-select: none;
      }
      #tray {
        display: flex;
        align-items: flex-start;
        overflow-x: auto;
        padding: 8px 12px;
        -webkit-overflow-scrolling: touch;
      }
      #tray::-webkit-scrollbar { display: none; }
      .item {
        flex: 0 0 auto;
        width: 74px;
        display: flex;
        flex-direction: column;
        align-items: center;
        border: 0;
        padding: 0;
        background: none;
        font-family: inherit;
      }
      .item + .item { margin-left: 10px; }
      .avatar { position: relative; width: 68px; height: 68px; }
      /* The mask keeps only the outer 3px of the gradient, so the gap between the ring and the
         cover is transparent and the app background shows through it. */
      .ring {
        position: absolute;
        top: 0; left: 0; right: 0; bottom: 0;
        border-radius: 50%;
        background: linear-gradient(45deg, #FEDA75, #FA7E1E, #D62976, #962FBF, #4F5BD5);
        -webkit-mask-image: radial-gradient(farthest-side, transparent 31px, #000 31px);
        mask-image: radial-gradient(farthest-side, transparent 31px, #000 31px);
      }
      .seen .ring { background: #C7C7CC; }
      .cover {
        position: absolute;
        top: 6px; left: 6px;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
      }
      .title {
        margin-top: 6px;
        max-width: 74px;
        font-size: 11px;
        line-height: 14px;
        font-weight: 600;
        color: #000;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .seen .title { font-weight: 400; }
      .pressed { transform: scale(0.94); transition: transform 0.12s; }
      #mock-badge {
        position: absolute;
        top: 2px; right: 6px;
        font-size: 10px;
        font-weight: 700;
        color: #FF3B30;
        pointer-events: none;
      }
      @media (prefers-color-scheme: dark) {
        .title { color: #FFF; }
        .seen .ring { background: #48484A; }
      }
    </style>
    </head>
    <body>
    <div id="tray"></div>
    <div id="mock-badge">MOCK</div>
    <script>
      var SCENARIO = "__SCENARIO__";
      var SLOW_DELAY_MS = 15000;
      var EMPTY_DELAY_MS = 2000;

      var STORIES = [
        { id: "new-arrivals", title: "Новинки", symbol: "✨", cover: "#FFE6DE", seen: false },
        { id: "sales", title: "Скидки", symbol: "🔥", cover: "#FFF0D4", seen: false },
        { id: "how-it-works", title: "Инструкция", symbol: "💡", cover: "#DEEDFF", seen: false },
        { id: "reviews", title: "Отзывы", symbol: "💬", cover: "#E0F7E8", seen: true },
        { id: "delivery", title: "Доставка", symbol: "🚚", cover: "#EBE6FF", seen: true },
        { id: "bonuses", title: "Бонусы", symbol: "🎁", cover: "#FFE6F0", seen: false }
      ];

      function post(payload) {
        var json = JSON.stringify(payload);
        if (window.mindboxStoriesFeed && window.mindboxStoriesFeed.postMessage) {
          window.mindboxStoriesFeed.postMessage(json);
          return;
        }
        var handlers = window.webkit && window.webkit.messageHandlers;
        if (handlers && handlers.mindboxStoriesFeed) {
          handlers.mindboxStoriesFeed.postMessage(json);
        }
      }

      function trayHeight() {
        return Math.ceil(document.getElementById("tray").getBoundingClientRect().height);
      }

      function render() {
        var tray = document.getElementById("tray");
        for (var i = 0; i < STORIES.length; i++) {
          tray.appendChild(makeItem(STORIES[i]));
        }
      }

      function makeItem(story) {
        var item = document.createElement("button");
        item.className = story.seen ? "item seen" : "item";
        item.setAttribute("data-id", story.id);

        var avatar = document.createElement("div");
        avatar.className = "avatar";

        var ring = document.createElement("div");
        ring.className = "ring";

        var cover = document.createElement("div");
        cover.className = "cover";
        cover.style.background = story.cover;
        cover.textContent = story.symbol;

        var title = document.createElement("div");
        title.className = "title";
        title.textContent = story.title;

        avatar.appendChild(ring);
        avatar.appendChild(cover);
        item.appendChild(avatar);
        item.appendChild(title);

        item.addEventListener("click", function () {
          // The page marks previews as seen: the native side knows nothing about preview state.
          item.className = "item seen";
          item.classList.add("pressed");
          setTimeout(function () { item.classList.remove("pressed"); }, 120);
          post({ type: "storyTapped", storyId: story.id });
        });

        return item;
      }

      function reportHeightChange() {
        var height = trayHeight();
        // Zero height is the "nothing to show" verdict; the observer never issues it, so an
        // intermediate relayout cannot collapse a feed that is already shown.
        if (height > 0) {
          post({ type: "heightChanged", height: height });
        }
      }

      function reportReady() {
        post({ type: "ready", height: trayHeight() });

        // The observer connects after ready: when the content changes, the height follows by
        // itself with no native involvement. That is the adaptive-height contract.
        if (window.ResizeObserver) {
          new ResizeObserver(reportHeightChange).observe(document.getElementById("tray"));
        } else {
          window.addEventListener("resize", reportHeightChange);
        }
      }

      if (SCENARIO !== "EMPTY" && SCENARIO !== "ERROR") {
        render();
      }

      // The height goes out after the layout, not on DOMContentLoaded: before layout it is zero.
      window.addEventListener("load", function () {
        if (SCENARIO === "ERROR") {
          // A broken page: it never answers, the container's timeout collapses the block.
          return;
        }
        if (SCENARIO === "EMPTY") {
          // Targeting matched nothing: zero height is the explicit "nothing to show" verdict.
          // Delayed like real life — the emptiness is only known after the backend answers,
          // so the block shows its placeholder for a couple of seconds first.
          setTimeout(function () { post({ type: "ready", height: 0 }); }, EMPTY_DELAY_MS);
          return;
        }
        if (SCENARIO === "SLOW") {
          setTimeout(reportReady, SLOW_DELAY_MS);
          return;
        }
        reportReady();
      });
    </script>
    </body>
    </html>
    """.trimIndent()
}
