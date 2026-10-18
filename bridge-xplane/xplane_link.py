#!/usr/bin/env python3
"""
OpenTakeoff Link Bridge for X-Plane 12

Protokół kompatybilny z OpenTakeoff Link dla MSFS:
- WebSocket server na porcie 7878
- JSON messages format
- UDP discovery broadcast
- X-Plane DataRefs → OpenTakeoff format

Wymagania:
- Python 3.8+
- XPPython3 plugin w X-Plane
- websockets library

Instalacja:
1. Zainstaluj XPPython3 w X-Plane
2. Skopiuj ten plik do X-Plane/Resources/plugins/PythonPlugins/
3. pip install websockets
"""

import asyncio
import json
import socket
import time
from typing import Dict, Any, Optional
import websockets

# X-Plane DataRefs mapping
DATAREFS = {
    # Position
    'latitude': 'sim/flightmodel/position/latitude',
    'longitude': 'sim/flightmodel/position/longitude',
    'altitude_msl': 'sim/flightmodel/position/elevation',
    'altitude_agl': 'sim/flightmodel/position/y_agl',

    # Attitude
    'pitch': 'sim/flightmodel/position/theta',
    'bank': 'sim/flightmodel/position/phi',
    'heading_true': 'sim/flightmodel/position/psi',
    'heading_mag': 'sim/flightmodel/position/mag_psi',

    # Speed
    'ias': 'sim/flightmodel/position/indicated_airspeed',
    'tas': 'sim/flightmodel/position/true_airspeed',
    'gs': 'sim/flightmodel/position/groundspeed',
    'vs': 'sim/flightmodel/position/vh_ind_fpm',

    # Controls
    'throttle': 'sim/flightmodel/engine/ENGN_thro[0]',
    'mixture': 'sim/cockpit2/engine/actuators/mixture_ratio[0]',
    'prop': 'sim/flightmodel/engine/ENGN_prop[0]',
    'flaps': 'sim/flightmodel/controls/flaprqst',
    'gear': 'sim/cockpit/switches/gear_handle_status',

    # Engine
    'n1': 'sim/flightmodel/engine/ENGN_N1_[0]',
    'n2': 'sim/flightmodel/engine/ENGN_N2_[0]',
    'rpm': 'sim/flightmodel/engine/ENGN_tacrad[0]',
    'egt': 'sim/flightmodel/engine/ENGN_EGT_c[0]',

    # Autopilot
    'ap_master': 'sim/cockpit/autopilot/autopilot_mode',
    'ap_altitude': 'sim/cockpit/autopilot/altitude',
    'ap_heading': 'sim/cockpit/autopilot/heading',
    'ap_speed': 'sim/cockpit/autopilot/airspeed',

    # Aircraft
    'on_ground': 'sim/flightmodel/failures/onground_any',
    'title': 'sim/aircraft/view/acf_descrip',
}

class XPlaneLink:
    def __init__(self):
        self.clients = set()
        self.running = False
        self.dataref_values = {}

    async def start(self):
        """Start WebSocket server and UDP discovery."""
        self.running = True

        # Start WebSocket server
        server = await websockets.serve(
            self.handle_client,
            "0.0.0.0",
            7878
        )

        # Start UDP discovery broadcast
        asyncio.create_task(self.broadcast_discovery())

        # Start data update loop
        asyncio.create_task(self.update_loop())

        print("OpenTakeoff Link for X-Plane started on port 7878")
        await asyncio.Future()  # Run forever

    async def handle_client(self, websocket, path):
        """Handle WebSocket client connection."""
        self.clients.add(websocket)
        print(f"Client connected: {websocket.remote_address}")

        try:
            # Send initial state
            await self.send_state(websocket)

            # Handle incoming messages
            async for message in websocket:
                await self.handle_message(websocket, message)
        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            self.clients.remove(websocket)
            print(f"Client disconnected: {websocket.remote_address}")

    async def handle_message(self, websocket, message: str):
        """Handle incoming message from client."""
        try:
            data = json.loads(message)
            msg_type = data.get('type')

            if msg_type == 'command':
                # Execute X-Plane command
                command = data.get('command')
                self.execute_command(command)

            elif msg_type == 'set_dataref':
                # Set DataRef value
                dataref = data.get('dataref')
                value = data.get('value')
                self.set_dataref(dataref, value)

        except json.JSONDecodeError:
            print(f"Invalid JSON: {message}")

    async def update_loop(self):
        """Send state updates to all clients."""
        while self.running:
            # Read DataRefs from X-Plane
            self.read_datarefs()

            # Send to all clients
            state = self.build_state()
            message = json.dumps(state)

            # Broadcast to all connected clients
            if self.clients:
                await asyncio.gather(
                    *[client.send(message) for client in self.clients],
                    return_exceptions=True
                )

            await asyncio.sleep(0.1)  # 10 Hz update rate

    def read_datarefs(self):
        """Read all DataRefs from X-Plane."""
        # TODO: Use XPPython3 API to read DataRefs
        # For now, placeholder values
        self.dataref_values = {
            'latitude': 52.0,
            'longitude': 19.0,
            'altitude_msl': 1000.0,
            'altitude_agl': 50.0,
            'pitch': 0.0,
            'bank': 0.0,
            'heading_true': 90.0,
            'ias': 120.0,
            'tas': 125.0,
            'gs': 120.0,
            'vs': 0.0,
            'on_ground': False,
        }

    def build_state(self) -> Dict[str, Any]:
        """Build OpenTakeoff-compatible state message."""
        return {
            'type': 'state',
            'timestamp': int(time.time() * 1000),
            'position': {
                'lat': self.dataref_values.get('latitude'),
                'lon': self.dataref_values.get('longitude'),
                'altMslFt': self.dataref_values.get('altitude_msl'),
                'altAglFt': self.dataref_values.get('altitude_agl'),
            },
            'attitude': {
                'pitchDeg': self.dataref_values.get('pitch'),
                'bankDeg': self.dataref_values.get('bank'),
                'hdgTrueDeg': self.dataref_values.get('heading_true'),
            },
            'speed': {
                'iasKt': self.dataref_values.get('ias'),
                'tasKt': self.dataref_values.get('tas'),
                'gsKt': self.dataref_values.get('gs'),
                'vsFpm': self.dataref_values.get('vs'),
            },
            'aircraft': {
                'onGround': self.dataref_values.get('on_ground'),
                'title': self.dataref_values.get('title', 'X-Plane Aircraft'),
            },
            'controls': {},
            'autopilot': {},
            'engine': {},
        }

    def execute_command(self, command: str):
        """Execute X-Plane command."""
        # TODO: Use XPPython3 API
        print(f"Execute command: {command}")

    def set_dataref(self, dataref: str, value: Any):
        """Set X-Plane DataRef value."""
        # TODO: Use XPPython3 API
        print(f"Set DataRef: {dataref} = {value}")

    async def broadcast_discovery(self):
        """Broadcast UDP discovery packets."""
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        message = json.dumps({
            'type': 'discovery',
            'simulator': 'X-Plane',
            'version': '1.0.0',
            'port': 7878
        }).encode()

        while self.running:
            try:
                sock.sendto(message, ('255.255.255.255', 7879))
            except Exception as e:
                print(f"Discovery broadcast error: {e}")

            await asyncio.sleep(2)  # Broadcast every 2 seconds

def main():
    """Entry point."""
    link = XPlaneLink()
    asyncio.run(link.start())

if __name__ == '__main__':
    main()
