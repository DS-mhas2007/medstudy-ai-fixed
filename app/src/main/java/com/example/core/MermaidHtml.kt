package com.example.core

/**
 * HTML page that renders one Mermaid diagram inside a WebView using the Mermaid library bundled in
 * assets (no CDN, so it works offline and cannot be tampered with). The diagram text is HTML-escaped, so
 * model output can never inject markup or script, and Mermaid runs with securityLevel "strict".
 */
object MermaidHtml {
    const val SCRIPT_ASSET = "mermaid.min.js"

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun page(code: String): String {
        val body = escape(code.replace("`", "").trim())
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0, user-scalable=yes">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; img-src data:; font-src data:;">
            <style>
              html, body { margin: 0; padding: 0; background: #07152D; color: #EEF2F7; }
              body { padding: 12px; box-sizing: border-box; font-family: sans-serif; }
              .mermaid { text-align: center; }
              svg { max-width: 100%; height: auto; }
              .edgeLabel { background-color: #07152D !important; color: #94A3B8 !important; }
            </style>
            <script src="$SCRIPT_ASSET"></script>
            <script>
              document.addEventListener("DOMContentLoaded", function () {
                mermaid.initialize({
                  startOnLoad: true,
                  securityLevel: "strict",
                  theme: "dark",
                  flowchart: { htmlLabels: false, curve: "basis" },
                  themeVariables: { darkMode: true, background: "#07152D", lineColor: "#D6B2FC", primaryTextColor: "#EEF2F7" }
                });
              });
            </script>
            </head>
            <body>
            <pre class="mermaid">$body</pre>
            </body>
            </html>
        """.trimIndent()
    }
}
