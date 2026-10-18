# Travle Trainer

A light geography trainer for the game Travle. Plain HTML, CSS and JavaScript, no framework and no build step needed to run it.

## Run it

Easiest: open `dist/travle-trainer-offline.html` in a browser. It is one file and needs no internet.

Or serve the folder:

    cd travle-trainer
    python3 scripts/serve.py        # or: npm start  (opens http://localhost:8000)

`scripts/serve.py` turns browser caching off, so you always see the latest files. The plain `python3 -m http.server` lets the browser keep old copies, which is why an update could look like nothing changed. The header shows the version (currently **v2**): if you see no version badge, or no Learn and Flags tabs, you are looking at an old copy, so press Cmd+Shift+R (Ctrl+Shift+R on Windows) once. If the server says the port is in use, an older server is still running: stop it with Ctrl+C in its terminal, or run `python3 scripts/serve.py 8001`.

Opening `index.html` straight from disk will not work, because browsers block loading the data files that way. Use the offline file or a server.

## Modes

- **Learn**: study a region with every country named. Names are placed so small countries stay readable (leader lines, more names appear as you zoom). Switch names off to test yourself: click a country or a flag to reveal it. A gallery lists every flag in the region. Optional flags beside the names. Buttons jump straight into Locate, Name, Flags, Neighbours or Route for the same region.
- **Locate**: a name appears, click the country. Tiny countries show as rings so they can be tapped.
- **Name**: a country is highlighted, type its name. Autocomplete, aliases (USA, Ivory Coast, DR Congo) and small typos are accepted.
- **Flags**: three question types. Flag to name (typed), flag to map (click the country), name to flag (pick from four, with look-alike flags as distractors; keys 1 to 4 work).
- **Neighbours**: click every country that shares a land border with the highlighted one.
- **Route**: Travle itself. Two countries are given, guess the ones between them. Green is on a shortest route, yellow and orange are detours.
- **Atlas**: hover for names, click for flag, capital, neighbours, sea links and how often a country sits on a shortest route.
- **Progress**: the map coloured by how well you know each country, plus weakest spots and mastery per skill (Locate, Name, Flags, Neighbours) and continent.

## Regions

World, six continents (Europe, Asia, Africa, North America including the Caribbean, South America, Oceania) and sub-regions such as Western and Central Europe, West Africa, the Balkans, the Middle East, the Caribbean and the Pacific Islands. Every mode respects the chosen region.

## Colour coding

The dropdown on the map colours the base map. Each mode remembers its own choice. Learn starts with neighbour colours.

- **By neighbour**: no two bordering countries share a colour, so borders are easy to see.
- **By region**: one colour per sub-region (per continent when viewing the world), with a legend.
- **Route importance**: darker means the country sits on a shortest route for more pairs of countries. These are the best first guesses in Travle.
- **My progress**: green for countries you know, red for those you miss, plain for new ones.

## Learning aids

- Locate, Name, Flags and Neighbours use spaced repetition: countries you miss come back more often, countries you know fade out. Progress is stored in your browser (localStorage), so use the same browser each time.
- After each answer a card shows the flag, name and capital together.
- "Show flags with country names" (Options) puts flags next to names in prompts and lists.

## Data

- `data/world.json`: Natural Earth 50m shapes (world-atlas), tagged with country ids.
- `data/countries.json`: names, regions, capitals, land borders (world-countries).
- `data/flags.json`: flag SVGs (country-flag-icons).
- `data/sea-links.json`: **hand-written** ferry, bridge and short-hop connections for island countries. Edit it to match the game's own rules; Route mode and the Atlas use it when "Count sea links" is on.

## Scripts

    npm install             # once, only needed to rebuild the data
    npm run build:data      # rebuild data/*.json from the npm packages
    npm run build:single    # write dist/travle-trainer.html and dist/travle-trainer-offline.html
    npm test                # logic tests (graph, matching, colouring, label layout, flag options)
