/**
 *  Enerwave Ceiling Mounted Motion Sensor
 *
 *  Copyright 2015 Adam Heinmiller
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
preferences 
{
    input("testMode", "boolean", title: "Enable Test Mode")    
    input("enableDebug", "boolean", title: "Enable Debug Messages")    
}

metadata 
{
    definition (namespace: "adamheinmiller", name: "Enerwave Ceiling Mounted Motion Sensor", author: "Adam Heinmiller") 
    {
        capability "Motion Sensor"
		capability "Battery"
        capability "Configuration"
        
        fingerprint deviceId:"0x2001", inClusters:"0x30, 0x84, 0x80, 0x85, 0x72, 0x86, 0x70"        
	}

    simulator 
    {
		status "Motion Started": "command: 2001, payload: FF"
		status "Motion Stopped": "command: 2001, payload: 00"
        
		status "Wakeup": "command: 8407, payload: 00"
         
		status "Battery Status 0%": "command: 8003, payload: FF"
		status "Battery Status 25%": "command: 8003, payload: 19"
        status "Battery Status 50%": "command: 8003, payload: 32"
        status "Battery Status 75%": "command: 8003, payload: 4B"
        status "Battery Status 100%": "command: 8003, payload: 64"

	}

    tiles 
    {
		standardTile("motion", "device.motion", width: 2, height: 2) 
        {
			state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
			state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
		}
        
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") 
        {
            state("battery", label:'${currentValue}% battery', unit:"")
        }

        main "motion"
        details(["motion", "battery"])
    }
}

def installed()
{
	
}

def updated()
{
	if (settings.testMode == "true")
    {
    	state.NextMotionDuration = 250
    }
    else
    {
    	state.NextMotionDuration = 5
	}
}

def configure()
{
	logMessage("Configuring Device")

     delayBetween([

		// Set device association for motion commands
        zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId:zwaveHubNodeId).format(),

        // Set motion sensor timeout to 5 minutes
        zwave.configurationV2.configurationSet(configurationValue: [5], parameterNumber: 0, size: 1).format(),
     
        // Set the wake up to 6 hours
        zwave.wakeUpV1.wakeUpIntervalSet(seconds:6 * 3600, nodeid:zwaveHubNodeId).format(),
        
		// Get initial battery report
        zwave.batteryV1.batteryGet().format()
        
     ])	
}



def parse(String description) 
{
	if (description == "updated") return;

	def cmd = zwave.parse(description)

	if (!cmd) return;
    
    logMessage(description)


    return zwaveEvent(cmd)
}


def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) 
{
    logCommand(cmd)

	def result = []
    
	//result << response(zwave.configurationV2.configurationGet())
	//result << response(zwave.associationV2.associationGet())
	//result << response(zwave.batteryV1.batteryGet())
    //result << response(zwave.wakeUpV2.wakeUpIntervalGet ())

	if (state.NextMotionDuration)
    {
    	result << response(zwave.configurationV2.configurationSet(configurationValue: [state.NextMotionDuration], parameterNumber: 0, size: 1))
        
        state.NextMotionDuration = null
    }
    
	result << response(zwave.wakeUpV2.wakeUpNoMoreInformation())
    
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) 
{
    logCommand(cmd)

    def result = [name: "battery", unit: "%", value: cmd.batteryLevel]
    
    if (cmd.batteryLevel == 255) result += [descriptionText: "Replace Batteries", value: 0]
    
    return createEvent(result)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
    logCommand(cmd)

	def result = []
    
    if (cmd.value == 255)
    {
    	result << createEvent([name: "motion", value: "active", descriptionText: "Detected Motion"])
    }
    else
    {
    	result << createEvent([name: "motion", value: "inactive", descriptionText: "Motion Has Stopped"])
    }
    
    return result
}


def zwaveEvent(physicalgraph.zwave.Command cmd) 
{
    logCommand("**Unhandled**: $cmd")
}

def logCommand(cmd)
{
	if (settings.enableDebug == "false") return
    
	log.debug "Device Command:  $cmd"
}

def logMessage(message)
{
	if (settings.enableDebug == "false") return

	log.debug message
}