# Cabinwright Battery

The car's battery levels and manual battery conditioning, for Kia/Hyundai Gen5W head units running
[NEMESIS](https://github.com/xwtk/hmg_nemesis). Written for the 2023 Kia EV6 (firmware
`CVEV.USA.0000.V015.012.260507`). NEMESIS package `cabinwright.battery`, installed from the
[Cabinwright store](https://github.com/cabinwright/store).

Start and Stop are on the screen the app opens to, so conditioning can be started on the way to a
charger. The store runs the session, so closing the app doesn't end it. It shows as running only
when the car itself says so. A start can take a minute or two, and the car may decline if the
battery doesn't need it.

All the car access goes through the store's `vehicle` and `conditioning` capabilities
(`CarVehicle` in the [SDK](https://github.com/cabinwright/sdk)), so it needs store 1.2.

## Build

```sh
git submodule update --init
sh build.sh     # -> dist/cabinwright.battery.zip, and R8's name map
sh sdk/api/tools/lint.sh .
```

Needs the Android SDK's build-tools 29.0.2, JDK 11 and Python 3.

## Credits

The battery conditioning request and the car's status signal were worked out by
[Launchpad](https://github.com/paytondevs/gen5w-preconditioning) (paytondevs), and the telematics
readings by [Battery Watchdog](https://github.com/zpoon/gen5w-BatteryWatchdog). This app shares
no code with either; each value was checked on an EV6.

## License

MIT (see `LICENSE`), Copyright (c) 2026 hazptr.
