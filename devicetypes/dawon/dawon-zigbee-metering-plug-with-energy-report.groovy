/**
 *  Copyright 2019 SmartThings
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

import physicalgraph.zigbee.zcl.DataType
import groovy.transform.Field

@Field version = "1.35"

metadata {
	definition (name: "Dawon Zigbee Metering Plug Power Consumption Report", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.smartplug", mnmn: "Dawon",  vid: "STES-1-Dawon-Zigbee_Smart_Plug") {
		capability "Energy Meter"
		capability "Power Meter"
		capability "Actuator"
		capability "Switch"
		capability "Refresh"
		capability "Health Check"
		capability "Sensor"
		capability "Configuration"
		capability "Power Consumption Report"

		command "reset"

		fingerprint manufacturer: "DAWON_DNS", model: "PM-B430-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug
		fingerprint manufacturer: "DAWON_DNS", model: "PM-B530-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug
		fingerprint manufacturer: "DAWON_DNS", model: "PM-C140-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS In-Wall Outlet
		fingerprint manufacturer: "DAWON_DNS", model: "PM-B540-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug
		fingerprint manufacturer: "DAWON_DNS", model: "ST-B550-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug
		fingerprint manufacturer: "DAWON_DNS", model: "PM-C150-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS In-Wall Outlet
		fingerprint manufacturer: "DAWON_DNS", model: "PM-C250-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS In-Wall Outlet
		fingerprint manufacturer: "DAWON_DNS", model: "PM-B440-ZB", deviceJoinName: "Dawon Outlet" // DAWON DNS Smart Plug
	}
	preferences {
		input name: "meterReadingDay", title: "?????????" , type: "number", range: "0..31", description: "?????????(???????????? ????????? ?????????. 0 ?????? ???????????? ?????? ??????, 29????????? ??????)", required: true, defaultValue: 0
		input name: "initEnergy", title: "?????????(kWh) ?????????(1??????)", type: "enum", options: ["Yes", "No"], description: "????????? ?????????(1?????? ???????????? ???????????? ???????????? No??? ????????????)", required: true, defaultValue: "No"
		input name: "resetMethod", title: "?????????(kWh) ????????? ??????", type: "enum", options: ["HW", "SW"], description: "????????? ????????? ??????(?????? ?????? ??????????????? ???????????? ???????????? HW ????????????, SW ????????? ?????? ??????????????? ?????????)", required: true, defaultValue: "SW"
		input name: "energyReport", title: "ST ???????????? ??????", type: "enum", options: ["Yes", "No"], description: "ST ???????????? ?????? ????????? ??????", required: true, defaultValue: "No"
		input type: "paragraph", element: "paragraph", title: "Version", description: "$version", displayDuringSetup: false
	}
}

def getATTRIBUTE_READING_INFO_SET() { 0x0000 }
def getATTRIBUTE_HISTORICAL_CONSUMPTION() { 0x0400 }

def initialize() {
	log.debug "$state.version initialize()"
	if (state.energyBias == null || state.energyBias == "" )			state.energyBias = 0
	if (state.energyOverflow == null || state.energyOverflow == "" )	state.energyOverflow = "No"
	if (state.prev == null || state.prev == "" )						state.prev = 0
	if (state.resetEnergy == null || state.resetEnergy == "" )			state.resetEnergy = 0
	state.version = version
}

def parse(String description) {
	log.debug "$state.version description is $description"
	def event = zigbee.getEvent(description)
	def descMap = zigbee.parseDescriptionAsMap(description)

	if (state.version != version)	initialize()

	checkDefaultSettings()

	if (event) {
		log.info "$state.version event enter:$event"
		if (event.name == "switch" && !descMap.isClusterSpecific && descMap.commandInt == 0x0B) {
			log.info "$state.version Ignoring default response with desc map: $descMap"
			return [:]
		} else if (event.name== "power") {
			event.value = event.value/getPowerDiv()
			event.unit = "W"
		} else if (event.name== "energy") {
			if (state.energyOverflow== "No" && event.value > 4000000000) {
				state.energyBias = event.value - state.prev
				state.energyOverflow = "Yes"
				log.debug "$state.version Energy bias is set to $state.energyBias (abnormal value)"
			} else if (state.energyOverflow== "Yes" && event.value < 4000000000) {
				state.energyBias = event.value - state.prev
				state.energyOverflow = "No"
				log.debug "$state.version Energy bias is reset to $state.energyBias (normal value)"
			}
			state.prev = event.value - state.energyBias
			if (state.resetEnergy == 1) {
				if (settings.resetMethod == "HW" ) {
					state.energyBias = 0
					state.prev = event.value
				}
				else if (settings.resetMethod == "SW") {
					state.energyBias = event.value
					state.prev = 0
				}
				state.resetEnergy = 0
				log.debug "$state.version Enery was reset to zero"
			}
			event.value = state.prev.toDouble()/getEnergyDiv()
			event.unit = "kWh"
		}
		log.info "$state.version event outer:$event"
		sendEvent(event)
	} else {
		List result = []
		log.debug "$state.version Desc Map: $descMap"

		List attrData = [[clusterInt: descMap.clusterInt ,attrInt: descMap.attrInt, value: descMap.value]]
		descMap.additionalAttrs.each {
			attrData << [clusterInt: descMap.clusterInt, attrInt: it.attrInt, value: it.value]
		}

		attrData.each {
			def map = [:]
			if (it.value && it.clusterInt == zigbee.SIMPLE_METERING_CLUSTER && it.attrInt == ATTRIBUTE_HISTORICAL_CONSUMPTION) {
				log.debug "$state.version power"
				map.name = "power"
				map.value = zigbee.convertHexToInt(it.value)/getPowerDiv()
				map.unit = "W"
			}
			else if (it.value && it.clusterInt == zigbee.SIMPLE_METERING_CLUSTER && it.attrInt == ATTRIBUTE_READING_INFO_SET) {
				log.debug "$state.version energy"
				map.name = "energy"
				def currentEnergy = zigbee.convertHexToInt(it.value)
				if (state.energyOverflow== "No" && currentEnergy > 4000000000) {
					state.energyBias = currentEnergy - state.prev
					state.energyOverflow = "Yes"
					log.debug "$state.version Energy bias is set to $state.energyBias (abnormal value)"
				} else if (state.energyOverflow== "Yes" && currentEnergy < 4000000000) {
					state.energyBias = currentEnergy - state.prev
					state.energyOverflow = "No"
					log.debug "$state.version Energy bias is set to $state.energyBias (normal value)"
				}
				state.prev = currentEnergy - state.energyBias
				if (state.resetEnergy == 1) {
					if (settings.resetMethod == "HW" ) {
						state.energyBias = 0
						state.prev = currentEnergy
					}
					else if (settings.resetMethod == "SW") {
						state.energyBias = currentEnergy
						state.prev = 0
					}
					state.resetEnergy = 0
					log.debug "$state.version Enery was reset to zero"
				}
				map.value = state.prev.toDouble()/getEnergyDiv()
				map.unit = "kWh"

				if (settings.energyReport == "Yes") {
					def currentPowerConsumption = device.currentState("powerConsumption")?.value
					Map previousMap = currentPowerConsumption ? new groovy.json.JsonSlurper().parseText(currentPowerConsumption) : [:]

					def deltaEnergy = calculateDelta (state.prev, previousMap)
					Map reportMap = [:]
					reportMap["energy"] = state.prev
					reportMap["deltaEnergy"] = deltaEnergy 
					sendEvent("name": "powerConsumption", "value": reportMap.encodeAsJSON(), displayed: false)
					log.debug "$state.version powerConsumption sendEvent $reportMap"
				}
			}

			if (map)	result << createEvent(map)

			log.debug "$state.version Parse returned $map"
		}
		return result
	}
}

def checkDefaultSettings() {
	if (settings.meterReadingDay == null || settings.meterReadingDay == "")	settings.meterReadingDay = 0
	if (settings.resetMethod == null || settings.resetMethod == "")			settings.resetMethod = "SW"
	if (settings.initEnergy == null || settings.initEnergy == "")			settings.initEnergy = "No"
	if (settings.energyReport == null || settings.energyReport == "")		settings.energyReport = "No"
	//log.debug "$settings.meterReadingDay $settings.resetMethod $settings.initEnergy $settings.energyReport"
}

BigDecimal calculateDelta (BigDecimal currentEnergy, Map previousMap) {
	if (previousMap?.'energy' == null)	return 0;

	BigDecimal lastAcumulated = BigDecimal.valueOf(previousMap ['energy']);
	return currentEnergy.subtract(lastAcumulated).max(BigDecimal.ZERO);
}

def off() {
	log.debug "$state.version off()"
	def cmds = zigbee.off()
	return cmds
}

def on() {
	log.debug "$state.version on()"
	def cmds = zigbee.on()
	return cmds
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	log.debug "$state.version ping()"
	return refresh()
}

def refresh() {
	log.debug "$state.version refresh"
	zigbee.onOffRefresh() +
	zigbee.electricMeasurementPowerRefresh() +
	zigbee.simpleMeteringPowerRefresh() +
	zigbee.readAttribute(zigbee.SIMPLE_METERING_CLUSTER, ATTRIBUTE_READING_INFO_SET)
}

def configure() {
	log.debug "$state.version Configuring Reporting"
	// this device will send instantaneous demand and current summation delivered every 1 minute
	sendEvent(name: "checkInterval", value: 2 * 60 + 10 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

	return refresh() +
		zigbee.onOffConfig() +
		zigbee.configureReporting(zigbee.SIMPLE_METERING_CLUSTER, ATTRIBUTE_READING_INFO_SET, DataType.UINT48, 1, 600, 1) +
		zigbee.electricMeasurementPowerConfig(1, 600, 0x01) +
		zigbee.simpleMeteringPowerConfig(1, 600, 0x01)
}

def installed() {
	log.debug "$state.version installed()"
	initialize()
}

def updated() {
	log.debug "$state.version updated()"
	if (settings.initEnergy == "Yes") {
		reset()
		device.updateSetting("initEnergy", [value: "No", type: "enum"])
	}
	checkDefaultSettings()
	unschedule(resetMeter)
	if (settings.meterReadingDay > 0 && settings.meterReadingDay < 29) {
		schedule("1 0 0 ${settings.meterReadingDay} * ?", resetMeter)
	}
	else if (settings.meterReadingDay >= 29) {
		schedule("1 0 0 L * ?", resetMeter)
	}
}

private int getPowerDiv() {
	1
}

private int getEnergyDiv() {
	1000
}

def resetMeter() {
	reset()
}

def reset(){
	log.debug "$state.version reset()"

	if ( settings.resetMethod == "HW" )
	{
		def pEnergy = device.currentState("energy").value
		sendEvent(name: "energy", value: pEnergy, unit: "kWh", displayed: true)
		zigbee.writeAttribute(zigbee.SIMPLE_METERING_CLUSTER, 0x0099, DataType.UINT8, 00)
	}
	state.resetEnergy = 1
}
