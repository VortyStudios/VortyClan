# VortyClan

Plugin clan untuk Minecraft dengan GUI sendiri, terinspirasi BetterTeams.

- Versi: 1.20.x sampai terbaru (dikompilasi dengan Paper API 1.20.1, Java 17)
- Software: Paper, Purpur, Spigot, Folia
- Opsional: PlaceholderAPI

## Build

```
mvn clean package
```

Hasil: `target/VortyClan-1.0.0.jar`. Taruh di folder `plugins/`.
Butuh akses internet ke `repo.papermc.io` dan `repo.extendedclip.com` saat build pertama.

## Perintah

`/clan` membuka GUI. Alias: `/clans`, `/vclan`.

| Perintah | Fungsi | Pangkat |
|---|---|---|
| `/clan create <nama> [tag]` | Buat clan | - |
| `/clan info [clan]` | Info clan | - |
| `/clan list [halaman]` | Peringkat clan | - |
| `/clan invite <pemain>` | Undang pemain | Admin |
| `/clan accept\|deny [clan]` | Terima/tolak undangan | - |
| `/clan join <clan>` | Gabung ke clan terbuka | - |
| `/clan leave` | Keluar | Anggota, Admin |
| `/clan kick\|ban\|unban <pemain>` | Kelola anggota | Admin |
| `/clan promote\|demote\|transfer <pemain>` | Ubah pangkat | Pemilik |
| `/clan sethome` / `/clan home` | Home clan | Admin / semua |
| `/clan open\|close` | Buka/tutup pendaftaran | Admin |
| `/clan pvp` | Friendly fire on/off | Admin |
| `/clan chat [pesan]` | Chat clan (toggle atau sekali kirim) | semua |
| `/clan desc <teks>` | Deskripsi | Admin |
| `/clan rename\|tag\|color` | Tampilan clan | Pemilik |
| `/clan disband` | Bubarkan clan | Pemilik |
| `/clan reload` | Muat ulang config & bahasa | `vortyclan.admin` |
| `/clan admin disband <clan>` | Bubarkan clan lain | `vortyclan.admin` |

## Permission

- `vortyclan.use` (default: semua) - pakai `/clan`
- `vortyclan.create` (default: semua) - buat clan
- `vortyclan.bypass.delay` (default: op) - home tanpa delay
- `vortyclan.admin` (default: op) - reload dan bubarkan clan lain

## PlaceholderAPI

Identifier: `vortyclan`

Pemain: `%vortyclan_name%`, `_tag`, `_color`, `_tag_colored`, `_role`, `_role_symbol`, `_members`,
`_online`, `_max_members`, `_score`, `_rank`, `_owner`, `_description`, `_open`, `_friendlyfire`,
`_has_clan`, `_invites`

Global: `%vortyclan_total_clans%`, `%vortyclan_top_<n>_name%`, `_tag`, `_score`, `_members`, `_owner`
(contoh: `%vortyclan_top_1_name%`)

## Bahasa

Ubah `language: id` atau `en` di `config.yml`. File pesan ada di `plugins/VortyClan/lang/` dan bisa diedit.
Mendukung kode `&` dan hex `&#RRGGBB`.

## Data

Disimpan di `plugins/VortyClan/data.yml` (YAML, disimpan async dan atomik).
