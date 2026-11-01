"""A fake MPRIS player on the session bus, for lyrics checks without a real music app.

    python3 checks/fakempris.py "<title>" "<artist>" <length seconds> [start position seconds]
Plays from the start position, advancing with wall time. MPRIS_NAME=spotify poses as Spotify.
"""
import os
import sys
import time

import gi

gi.require_version("Gio", "2.0")
from gi.repository import Gio, GLib  # noqa: E402

XML = """<node>
 <interface name="org.mpris.MediaPlayer2">
  <property name="Identity" type="s" access="read"/>
 </interface>
 <interface name="org.mpris.MediaPlayer2.Player">
  <method name="PlayPause"/><method name="Play"/><method name="Pause"/>
  <method name="Next"/><method name="Previous"/>
  <property name="PlaybackStatus" type="s" access="read"/>
  <property name="Metadata" type="a{sv}" access="read"/>
  <property name="Position" type="x" access="read"/>
 </interface>
</node>"""

title, artist, length = sys.argv[1], sys.argv[2], float(sys.argv[3])
started = time.time() - (float(sys.argv[4]) if len(sys.argv) > 4 else 0.0)
playing = True


def prop(_conn, _sender, _path, _iface, name):
    if name == "Identity":
        return GLib.Variant("s", "FakePlayer")
    if name == "PlaybackStatus":
        return GLib.Variant("s", "Playing" if playing else "Paused")
    if name == "Position":
        return GLib.Variant("x", int((time.time() - started) * 1_000_000))
    return GLib.Variant("a{sv}", {
        "mpris:trackid": GLib.Variant("o", "/fake/track/1"),
        "xesam:title": GLib.Variant("s", title),
        "xesam:artist": GLib.Variant("as", [artist]),
        "mpris:length": GLib.Variant("x", int(length * 1_000_000)),
    })


def call(_conn, _sender, _path, _iface, method, _params, invocation):
    global playing
    if method in ("PlayPause", "Play", "Pause"):
        playing = method == "Play" or (method == "PlayPause" and not playing)
    invocation.return_value(None)


def acquired(conn, _name):
    for iface in Gio.DBusNodeInfo.new_for_xml(XML).interfaces:
        conn.register_object("/org/mpris/MediaPlayer2", iface, call, prop, None)


Gio.bus_own_name(Gio.BusType.SESSION, "org.mpris.MediaPlayer2." + os.environ.get("MPRIS_NAME", "fakeplayer"),
                 Gio.BusNameOwnerFlags.NONE, acquired, None, None)
GLib.MainLoop().run()
