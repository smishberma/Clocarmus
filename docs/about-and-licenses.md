# About and bundled notices

Settings → About opens Privacy policy and Open source licenses. Both documents
are UTF-8 assets under `app/src/main/assets/about/`, read on the IO dispatcher and
displayed as selectable, wrapping paragraphs in a lazy scrolling list. They need
no network connection or browser. Each document retains its scroll position while
Settings is open and across activity recreation. Back returns from a document to
About, then Settings; long-press Back uses the existing home navigation cleanup.
The existing 50/50 layout, clock, transport and navigation crossfade remain in use.

The privacy policy describes current local data access, storage, launcher features,
system media integration and Android backup (`allowBackup=true`). Update it when
data handling changes. Its developer contact uses the public project issue tracker.
The in-app document does not provision a hosted policy URL for a store listing.

## License provenance and maintenance

`open_source_licenses.txt` lists the union of resolved binary artifacts in
`debugRuntimeClasspath` and `releaseRuntimeClasspath` as reviewed on 14 November
2026, including debug-only Compose tooling. The graph includes transitive AndroidX,
Guava, Okio, Kotlin, annotations and JSpecify artifacts, not just direct dependencies.
It includes the full Apache 2.0 text once for those components, the applicable
Kotlin standard library third-party notices, and Inter's complete OFL copyright
notice and license. No dependency or network permission was added for this UI.

Upstream sources used for the notices:

- [AndroidX](https://developer.android.com/jetpack/androidx) and
  [Media3 1.9.4 license](https://github.com/androidx/media/blob/1.9.4/LICENSE).
- [Lottie 6.7.1 license](https://github.com/airbnb/lottie-android/blob/v6.7.1/LICENSE).
- [Guava 33.3.1 license](https://github.com/google/guava/blob/v33.3.1/LICENSE).
- [Okio 1.17.6 license](https://github.com/square/okio/blob/okio-parent-1.17.6/LICENSE.txt).
- [Kotlin 2.2.10 license](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/LICENSE.txt)
  and [third-party inventory](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/README.md):
  GWT and Guava Apache attributions, ThreeTen BSD notice and Boost math license.
- [Coroutines 1.8.1 license](https://github.com/Kotlin/kotlinx.coroutines/blob/1.8.1/LICENSE.txt).
- [JetBrains annotations 23.0.0 license](https://github.com/JetBrains/java-annotations/blob/23.0.0/LICENSE.txt).
- [JSpecify 1.0.0 license](https://github.com/jspecify/jspecify/blob/v1.0.0/LICENSE).
- [Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1), copied from
  [`design/licenses/Inter-LICENSE.txt`](../design/licenses/Inter-LICENSE.txt).

When dependencies change, inspect both runtime graphs with
`./gradlew :app:dependencies --configuration debugRuntimeClasspath` and the
equivalent `releaseRuntimeClasspath` command. Update the bundled coordinates,
licenses and relevant upstream third-party/NOTICE text. Check inside AAR
`classes.jar` files as well as JARs for additional notices. Review new artifacts
rather than assuming they share their parent project's license. Normal builds
use the checked-in text without downloading notices.

For acceptance, open both documents, scroll through the final paragraphs, return
using system and on-screen Back, rotate/recreate the activity, and confirm that
reading does not interrupt playback or trigger an automatic return to the player.

## About and developer screens

About uses the supplied Clocarmus banner above the existing Settings action rows.
Developer opens the supplied avatar, Ismail Al Bermah credit, Twitter/X, LinkedIn,
GitHub and email shortcuts. Links delegate to external apps with `ACTION_VIEW`;
when no handler is installed, a toast explains that the link cannot open. Clocarmus
still has no network permission and does not fetch remote content itself.

Donate opens a small panel in the same half of the display with the Bitcoin address
`bc1q78uhgjzje48jxsshngsh5ted7dfv39x426ny2z` and a Copy address button. Copy writes
the exact address to Android's clipboard and confirms with a toast. It does not
initiate a payment. Close and Back return to Developer; Back from Developer returns
to About. Long-press Back retains the shared home cleanup behavior. The panels
use the shared crossfade, and recreation retains the current page.

Visual checks: `python3 scripts/compare-main-player.py --screen settings-about --install`
and `--screen settings-developer`. The debug-only `settings-donate` Settings preview
opens the donation panel for interaction checks. Also verify external link handling,
Copy address, document navigation and Back without interrupting playback.
