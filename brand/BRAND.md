# Mosaic brand assets

The Mosaic M is built from mirrored blue columns and orange triangles and a
central diamond: separate pieces composing a whole. Preserve this geometry and
the outlined wordmark. All supplied SVGs are true vectors with no installed-font
or raster dependency.

## Reusable identity

- [mosaic-mark.svg](mosaic-mark.svg): standalone transparent mark.
- [mosaic-lockup-light.svg](mosaic-lockup-light.svg): transparent mark and dark
  wordmark for white or other light backgrounds.
- [mosaic-lockup-dark.svg](mosaic-lockup-dark.svg): transparent mark and white
  wordmark for navy or other dark backgrounds.

The lockups have tight bounds and no tagline. Use these rather than cropping the
padded 240 × 120 README artwork in `.github/images/`. The README retains its
light/dark SVG presentations and matching PNG fallbacks.

## Colors

| Role | Value |
| --- | --- |
| Navy background | `#0d192a` |
| Blue | `#007def` |
| Orange | `#fd993e` |
| Wordmark on light backgrounds | `#181818` |
| Wordmark on dark backgrounds | `#ffffff` |

These are the canonical general-brand values. Social artwork also uses
`#b6c7dc` for secondary copy and `#142c48` / `#263244` for faint fragments;
these are decorative tones, not alternative logo colors. The historical light
README presentation intentionally retains blue `#1982fe` and orange `#fdb05e`
to match its raster fallback. Its dark presentation uses the canonical accents.

## Copy

Primary positioning: **Think from the response up, not the database down.**

Short descriptor: **Composable backend orchestration for Kotlin**

General branded destination: **BuildMosaic.org**. Preserve that capitalization.
Use `github.com/BuildMosaic/Mosaic` only in specifically GitHub contexts, such as
the repository preview image. Copy in the social SVGs is outlined; update the
outlines and corresponding PNG together when wording changes.

## Social package

SVG masters are the editable source. PNG exports are opaque sRGB images for
platform upload; the same banner serves X and Bluesky.

| Use | Editable master | PNG export (pixels) |
| --- | --- | --- |
| X profile | [avatar.svg](social/masters/avatar.svg) | [400 × 400](social/avatars/x-profile-400.png) |
| Bluesky profile | [avatar.svg](social/masters/avatar.svg) | [1000 × 1000](social/avatars/bluesky-profile-1000.png) |
| Threads profile | [avatar.svg](social/masters/avatar.svg) | [640 × 640](social/avatars/threads-profile-640.png) |
| GitHub organization/profile | [avatar.svg](social/masters/avatar.svg) | [500 × 500](social/github/github-logo-500.png) |
| X / Bluesky header | [banner.svg](social/masters/banner.svg) | [1500 × 500](social/banners/x-bluesky-banner-1500x500.png) |
| GitHub repository/link preview | [github-social-preview.svg](social/masters/github-social-preview.svg) | [1280 × 640](social/github/github-social-preview-1280x640.png) |
| Threads square post | [threads-post.svg](social/masters/threads-post.svg) | [1080 × 1080](social/threads/threads-post-1080.png) |

X recommends 400 × 400 profiles and 1500 × 500 headers; its headers can lose
about 60 pixels at either vertical edge. GitHub recommends approximately
500 × 500 profile images and 1280 × 640 repository previews, each under 1 MB.
Bluesky's client crops headers to 3:1 and avatars to a square; its image-upload
limit is 1 MB. The Bluesky and Threads avatar dimensions above are export
choices, not asserted platform mandates. Threads uses an avatar and posts;
there is no separate header in this package. Every supplied PNG is under 1 MB.

### Crop and phone-cutout guidance

The shared header leaves its top 270 pixels free of essential content. Essential
artwork fits within approximately x=530–1230, y=275–430 on the 1500 × 500 canvas.
Keep the top and bottom 60-pixel bands quiet and the lower-left x=0–420,
y=300–500 region clear for profile-avatar overlap. The faint fragments are
clipped away from those crop bands. Do not enlarge or move the lockup
into the top-center cutout region.

The package was checked at 360, 390, and 430 CSS-pixel viewport widths, with and
without X's 60-pixel top/bottom crop, against compact island, wide notch, and
centered camera-cutout simulations plus avatar overlap. These are conservative
layout checks, not a universal safe-area specification. Apps choose their own
header placement, zoom, and system insets; expanded Live Activities can occupy
more space. Recheck actual profile previews when uploading. Use operating-system
safe-area insets when placing artwork in an app rather than hardcoding a phone
model's cutout dimensions.

## Usage

Use the supplied vectors; preserve proportions and colors. Keep clear space of
at least one quarter of the mark's width around a standalone mark or lockup
(the tight SVG bounds do not include that padding). Prefer a mark at least
24 pixels tall and a horizontal lockup at least 180 pixels wide; use the mark
alone when lettering becomes too small. Avoid stretching, recoloring, adding
outlines, or redrawing the fragments. Rasterize from the SVG master at the
required dimensions, with antialiasing and sRGB tagging; retain a transparent
background for reusable identity assets and the opaque navy for social exports.

## Platform references

- [X profile and header guidance](https://help.x.com/en/managing-your-account/common-issues-when-uploading-profile-photo)
- [GitHub profile reference](https://docs.github.com/en/account-and-profile/reference/profile-reference)
- [GitHub repository preview guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview)
- [Bluesky header layout](https://github.com/bluesky-social/social-app/blob/main/src/view/com/util/UserBanner.tsx),
  [profile image limits](https://github.com/bluesky-social/social-app/blob/main/src/lib/constants.ts),
  [profile schema](https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/actor/profile.json)
- [Apple safe-area guidance](https://developer.apple.com/documentation/uikit/positioning-content-relative-to-the-safe-area)
- [Android display-cutout guidance](https://developer.android.com/design/ui/mobile/guides/layout-and-content/edge-to-edge)
