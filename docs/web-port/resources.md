# Web resources on Android

Reference for the compose resources in snapshot `57eda2a` and the Android files that replace them. Names follow one rule. `Res.drawable.<name>` becomes `R.drawable.<name>` with the same base name. A font file `Foo-Bar_Baz.ttf` becomes `R.font.foo_bar_baz` (lowercase, every non-alphanumeric run replaced by one underscore). The snapshot names are already in that shape, so the file name and the resource name match.

Drawables load with `androidx.compose.ui.res.painterResource`. Fonts load with `androidx.compose.ui.text.font.Font(R.font.<name>, weight, style)` from `res/font`. The web `Res.readBytes` call exists because the wasm font loader needs bytes. Android uses the font resource instead.

Grep of `Res\.` under `composeApp/src/wasmJsMain/kotlin` found `Res.drawable` and one `Res.readBytes`. It found no `Res.font`, no `Res.string`, and no `Res.getUri`. `LegacyQueueImporter.kt` uses a local `readRes` value. That is not the compose `Res` object.

## Drawables

PNG files live in `src/main/res/drawable-nodpi/`. Vector files live in `src/main/res/drawable/`.

| Web reference | Call site | Android replacement |
| --- | --- | --- |
| `Res.drawable.highlight_texture_cobalt` | `ui/highlights/RepeatableBestPoster.kt` line 24, `ui/highlights/CadenceBandsPoster.kt` line 38 | `painterResource(R.drawable.highlight_texture_cobalt)` |
| `Res.drawable.highlight_texture_magenta` | `ui/highlights/RunningTotalPoster.kt` line 44 | `painterResource(R.drawable.highlight_texture_magenta)` |
| `Res.drawable.highlight_texture_orange` | `ui/highlights/BeatPoster.kt` line 24, `ui/highlights/MatchCutPoster.kt` line 36 | `painterResource(R.drawable.highlight_texture_orange)` |
| `Res.drawable.progress_truck_hero` | `ui/navigation/ProgressScreenV2.kt` line 2495 | `painterResource(R.drawable.progress_truck_hero)` |
| `Res.drawable.quiet_calendar` | `ui/theme/QuietDepth.kt` line 43, unselected | `painterResource(R.drawable.quiet_calendar)` |
| `Res.drawable.quiet_calendar_active` | `ui/theme/QuietDepth.kt` line 43, selected | `painterResource(R.drawable.quiet_calendar_active)` |
| `Res.drawable.quiet_progress` | `ui/theme/QuietDepth.kt` line 44, unselected | `painterResource(R.drawable.quiet_progress)` |
| `Res.drawable.quiet_progress_active` | `ui/theme/QuietDepth.kt` line 44, selected | `painterResource(R.drawable.quiet_progress_active)` |
| `Res.drawable.quiet_sparkles` | `ui/theme/QuietDepth.kt` line 45, unselected | `painterResource(R.drawable.quiet_sparkles)` |
| `Res.drawable.quiet_sparkles_active` | `ui/theme/QuietDepth.kt` line 45, selected | `painterResource(R.drawable.quiet_sparkles_active)` |
| `Res.drawable.quiet_heart` | `ui/theme/QuietDepth.kt` line 46, unselected | `painterResource(R.drawable.quiet_heart)` |
| `Res.drawable.quiet_heart_active` | `ui/theme/QuietDepth.kt` line 46, selected | `painterResource(R.drawable.quiet_heart_active)` |
| `Res.drawable.quiet_split` | `ui/theme/QuietDepth.kt` line 47, unselected | `painterResource(R.drawable.quiet_split)` |
| `Res.drawable.quiet_split_active` | `ui/theme/QuietDepth.kt` line 47, selected | `painterResource(R.drawable.quiet_split_active)` |
| `Res.drawable.quiet_search` | `ui/theme/QuietDepth.kt` line 48 | `painterResource(R.drawable.quiet_search)` |
| `Res.drawable.quiet_left` | `ui/theme/QuietDepth.kt` line 49 | `painterResource(R.drawable.quiet_left)` |
| `Res.drawable.quiet_right` | `ui/theme/QuietDepth.kt` line 50 | `painterResource(R.drawable.quiet_right)` |
| `Res.drawable.quiet_down` | `ui/theme/QuietDepth.kt` line 51 | `painterResource(R.drawable.quiet_down)` |
| `Res.drawable.quiet_trophy` | `ui/theme/QuietDepth.kt` line 52 | `painterResource(R.drawable.quiet_trophy)` |
| `Res.drawable.quiet_dumbbell` | `ui/theme/QuietDepth.kt` line 53 | `painterResource(R.drawable.quiet_dumbbell)` |
| `Res.drawable.quiet_arrow` | `ui/theme/QuietDepth.kt` line 54 | `painterResource(R.drawable.quiet_arrow)` |

These seven vectors exist and have no `Res.drawable` call in the snapshot. `QuietIcon` does not take the active art for those names.

| File | Android resource |
| --- | --- |
| `quiet_arrow_active.svg` | `R.drawable.quiet_arrow_active` |
| `quiet_down_active.svg` | `R.drawable.quiet_down_active` |
| `quiet_dumbbell_active.svg` | `R.drawable.quiet_dumbbell_active` |
| `quiet_left_active.svg` | `R.drawable.quiet_left_active` |
| `quiet_right_active.svg` | `R.drawable.quiet_right_active` |
| `quiet_search_active.svg` | `R.drawable.quiet_search_active` |
| `quiet_trophy_active.svg` | `R.drawable.quiet_trophy_active` |

Stroke is `#FF000000` and fill is `#00000000`. `androidx.compose.material3.Icon` tints that black stroke. The transparent fill stays empty. Tint stays on the Compose call.

## Fonts

`ui/theme/FontLoader.kt` line 19 is `Res.readBytes("files/fonts/$file.ttf")`. Each stem below is one `font(...)` argument in that file. The Android replacement is `Font(R.font.<stem>, weight, style)` from `androidx.compose.ui.text.font.Font`.

| Stem | Weight | Style | Android call |
| --- | --- | --- | --- |
| `arimo_regular` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.arimo_regular, FontWeight.Normal)` |
| `arimo_medium` | `FontWeight.Medium` | `FontStyle.Normal` | `Font(R.font.arimo_medium, FontWeight.Medium)` |
| `arimo_bold` | `FontWeight.Bold` | `FontStyle.Normal` | `Font(R.font.arimo_bold, FontWeight.Bold)` |
| `satoshi_regular` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.satoshi_regular, FontWeight.Normal)` |
| `satoshi_medium` | `FontWeight.Medium` | `FontStyle.Normal` | `Font(R.font.satoshi_medium, FontWeight.Medium)` |
| `satoshi_bold` | `FontWeight.Bold` | `FontStyle.Normal` | `Font(R.font.satoshi_bold, FontWeight.Bold)` |
| `clash_grotesk_semibold` | `FontWeight.SemiBold` | `FontStyle.Normal` | `Font(R.font.clash_grotesk_semibold, FontWeight.SemiBold)` |
| `clash_grotesk_bold` | `FontWeight.Bold` | `FontStyle.Normal` | `Font(R.font.clash_grotesk_bold, FontWeight.Bold)` |
| `archivo_black` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.archivo_black, FontWeight.Normal)` |
| `caveat_variable` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.caveat_variable, FontWeight.Normal)` |
| `league_gothic_condensed` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.league_gothic_condensed, FontWeight.Normal)` |
| `anton_regular` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.anton_regular, FontWeight.Normal)` |
| `ecolift_impact_condensed` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.ecolift_impact_condensed, FontWeight.Normal)` |
| `fraunces_light` | `FontWeight.Light` | `FontStyle.Normal` | `Font(R.font.fraunces_light, FontWeight.Light)` |
| `fraunces_light_italic` | `FontWeight.Light` | `FontStyle.Italic` | `Font(R.font.fraunces_light_italic, FontWeight.Light, FontStyle.Italic)` |
| `space_grotesk_regular` | `FontWeight.Normal` | `FontStyle.Normal` | `Font(R.font.space_grotesk_regular, FontWeight.Normal)` |
| `space_grotesk_medium` | `FontWeight.Medium` | `FontStyle.Normal` | `Font(R.font.space_grotesk_medium, FontWeight.Medium)` |
| `space_grotesk_semibold` | `FontWeight.SemiBold` | `FontStyle.Normal` | `Font(R.font.space_grotesk_semibold, FontWeight.SemiBold)` |

Seven of these files were already in `src/main/res/font/` with the same SHA-256 as the snapshot. `archivo_black.ttf`, `caveat_variable.ttf`, `clash_grotesk_bold.ttf`, `clash_grotesk_semibold.ttf`, `satoshi_bold.ttf`, `satoshi_medium.ttf`, `satoshi_regular.ttf`. The copy does not change their bytes. The other eleven files are new.

## Licenses

Copied from the snapshot into `src/main/assets/licenses/` with the same file names.

| Snapshot file | Android file |
| --- | --- |
| `files/fonts/anton_OFL.txt` | `src/main/assets/licenses/anton_OFL.txt` |
| `files/fonts/arimo_OFL.txt` | `src/main/assets/licenses/arimo_OFL.txt` |
| `files/fonts/league_gothic_OFL.txt` | `src/main/assets/licenses/league_gothic_OFL.txt` |
| `files/licenses/lucide.txt` | `src/main/assets/licenses/lucide.txt` |

The snapshot has no sidecar license for Archivo Black, Caveat, Clash Grotesk, Fraunces, Satoshi, Space Grotesk, or `ecolift_impact_condensed`.

## Converter

`docs/web-port/svg_to_vector.py` reads a directory of `.svg` files and writes one vector drawable per file. The command is `python docs/web-port/svg_to_vector.py SRC_DIR DST_DIR`, with `SRC_DIR` the snapshot `drawable` directory and `DST_DIR` `src/main/res/drawable`. A second run overwrites the same files and leaves identical bytes in place.

The script keeps an untransformed `path` `d` string as `android:pathData`. It turns `circle`, `ellipse`, `rect`, `line`, `polyline`, and `polygon` into paths. It bakes `transform` on a shape or a group into the path. A uniform scale multiplies `strokeWidth`. A non-uniform scale uses the average of the two axis scales and prints a warning. Group `opacity` is multiplied into each path's fill alpha and stroke alpha.

The script rejects `text`, `image`, `use`, gradients, masks, clips, and filters. `currentColor` becomes `#FF000000`. None of the 24 snapshot icons use a transform, opacity, gradient, mask, clip, filter, text, image, or `use`. `line` and `polyline` are implemented and covered by the script's self-test. No snapshot icon contains those two elements.
