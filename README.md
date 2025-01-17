# Mobile P2PFL Integration

**The purpose of this app is to work as a platform for integrating Peer-to-Peer Federated Learning (P2PFL) models, enabling decentralized training and data privacy while ensuring smooth user experience across devices.**

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Development Environment Setup](#development-environment-setup)
  - [P2P Environment Installation](#p2p-environment-installation)
  - [Mobile App Setup](#mobile-app-setup)
- [Connecting Physical Devices](#connecting-physical-devices)
  - [Port Forwarding with `netsh` on Windows](#port-forwarding-with-netsh-on-windows)
- [Running and Testing the System](#running-and-testing-the-system)
  - [Collaborative Training Tests](#collaborative-training-tests)

---

## Prerequisites

Before starting the installation and testing, ensure you have the following:

- A computer running Windows, macOS, or Linux.
- Basic knowledge of command-line tools and development environments like Android Studio.
- Access to a physical Android device or a configured emulator.
- For Windows users, WSL (Windows Subsystem for Linux) simplifies the execution of the P2P system (additional setup steps are explained below).
- Python installation (3.9 to 3.11).
- Required libraries and dependencies (details provided later).

---

## Development Environment Setup

### P2P Environment Installation

1. Download and install Python 3.9 to 3.11 from the [official Python website](https://www.python.org).
2. Install Poetry, a modern dependency manager for Python:
   ```bash
   pip install poetry
   ```
3. Clone the official P2P system repository from GitHub:
   ```bash
   git clone https://github.com/BoscoSO/p2pfl.git
   cd p2pfl
   ```
4. Switch to the `feature/proxy-node` branch:
   ```bash
   git checkout feature/proxy-node
   ```
5. Use Poetry to install the required dependencies:
   ```bash
   poetry install --all-extras
   ```
   This command installs both standard and optional dependencies needed for advanced features.
6. Verify that all libraries are installed correctly:
   ```bash
   python -c "import tensorflow as tf; print(tf.__version__)"
   ```
   This should output the installed TensorFlow version.
7. Start the proxy node using the provided examples, specifying the desired IP address and port:
   ```bash
   poetry run python p2pfl/examples/proxy_node.py --addr 172.30.231.18:50051
   ```
   This runs the proxy and waits for the order to start collaborative training.

---

### Mobile App Setup

1. Download and install Android Studio from the [official website](https://developer.android.com/studio).
2. Clone the mobile app repository:
   ```bash
   git clone https://github.com/BoscoSO/Mobile_p2pfl.git
   ```
3. Open the project in Android Studio and configure the environment by selecting a connected physical device or creating an emulator with at least Android API Level 24.
4. Build and install the application on the device or emulator by selecting `Run > Run 'app'`.

---

## Connecting Physical Devices

The app is configured to connect to the proxy at `172.30.231.18:50051`. If the proxy runs at this address and an emulator is used on the same machine, connectivity issues should not occur. However, to connect physical devices, ensure they are on the same network as the proxy. Depending on your operating system, you may need to manually configure the connections to allow access to the proxy port from external devices.

---

### Port Forwarding with `netsh` on Windows

On Windows, you need to forward the port used by the proxy to the IP address assigned by WSL (Windows Subsystem for Linux) so external devices can connect. Use the following commands:

```bash
netsh interface portproxy add v4tov4 listenport=50051 listenaddress=0.0.0.0 connectport=50051 connectaddress=172.30.231.18
netsh advfirewall firewall add rule name="Allow GRPC 50051" protocol=TCP dir=in localport=50051 action=allow
```

#### Command Descriptions

- **`netsh interface portproxy add v4tov4`:** Redirects incoming connections on port `50051` across all interfaces (`0.0.0.0`) to the WSL-assigned IP `172.30.231.18` on the same port.
- **`netsh advfirewall firewall add rule`:** Adds a Windows Firewall rule to allow incoming TCP connections on port `50051`.

#### Removing the Configuration

To undo the above setup, use the following commands:

```bash
netsh interface portproxy delete v4tov4 listenport=50051 listenaddress=0.0.0.0
netsh advfirewall firewall delete rule name="Allow GRPC 50051"
```

---

## Running and Testing the System

### Collaborative Training Tests

1. Start the P2P proxy with the desired number of rounds and iterations.
2. Launch the mobile app on the configured devices or emulators.
3. Connect clients through the app interface.
4. Press any key in the proxy terminal to initiate collaborative training.
5. Monitor the execution in real-time using Android Logs and the proxy terminal. The app also displays progress on the same screen where connections are made.

---

```
