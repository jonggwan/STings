/**
 *  EZEX POWER METER NewApp V0.5
 *
 *  Copyright 2020 YSB
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

// 8/27 계절/하계 요금수정.
// 8/30 UI변경 
// 9/8 기준일 변경시 스케줄 정상반영되도록 수정.
// 9/29 검침일 표기부분 수정, 이번달 누적전력 커스텀초기화 메뉴 추가
// 2021-7-26 2021년 전기요금체제로 개편. 관련 요율을 하나의 배열로만 저장하여 코딩 오류 및 추후 변동시에 대응이 편하도록 함.

import groovy.json.JsonOutput
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata{  definition (name: "EZex Power Meter Progressive Tax", 
		namespace: "jonggwan", 
		author: "YooSangBeom w/ JG",
		mnmn: "SmartThingsCommunity", 
		vid: "11a50ae1-1ff2-3053-877c-005c0e788120")
	{ 
        capability "Energy Meter"
        capability "Power Meter"
        capability "Refresh"
        capability "Health Check"
        capability "Sensor"
        capability "Configuration"
        capability "jonggwan.thismonthenergy"        
        capability "jonggwan.electriccharges"
        capability "jonggwan.powerconsumptionstep"
        capability "jonggwan.meterreadingdate"
        //capability "jonggwan.season"
        capability "jonggwan.etcseason"
        capability "jonggwan.summerseason"

        
        attribute "kwhTotal", "number"		// this is value reported by the switch since joining the hub.  See change log above for details.
        attribute "resetTotal", "number"	           // used to calculate accumulated kWh after a reset by the user.  See change log above for details.     	       
        
        command "reset"

        fingerprint profileId: "0104", deviceId:"0053", inClusters: "0000, 0003, 0004, 0B04, 0702", outClusters: "0019", manufacturer: "", model: "E240-KR080Z0-HA", deviceJoinName: "EZEX Energy Monitor(CT)"
        
    }
    
	preferences 
    {       
        input name: "MeterReadingDate", title:"검침일" , type: "text", required: true, defaultValue: 7
        input name: "LastMonthWatt", title:"지난달누적전력(초기화용)" , type: "text", required: true, defaultValue: 0
	}
   // tile definitions
    tiles(scale: 2) 
	{
        multiAttributeTile(name:"power", type: "generic", width: 5, height: 5)
        {
            tileAttribute("device.power", key: "PRIMARY_CONTROL") 
            {
                attributeState("default", label:'${currentValue} Watt')
            }
            tileAttribute("device.powerConsumption_step", key: "SECONDARY_CONTROL") 
            {
                attributeState("default", label:'${currentValue} 단계 적용 중! 아껴써요!', icon: "st.Appliances.appliances17")
            }
        }
        standardTile("ThisMonthEnergy", "device.ThisMonthEnergy", inactiveLabel: false, decoration: "flat", width: 2, height: 1) 
        {
            state "default", label:'이번달 누적 : ${currentValue} kWh'
        }    
        standardTile("powerConsumption", "device.powerConsumption", inactiveLabel: false, decoration: "flat", width: 2, height: 1) 
        {
            state "default", label:'기기누적 : ${currentValue} kWh'
        }     
        standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 1) 
        {
            state "default", label:'reset kWh', action:"reset", icon: "st.secondary.refresh-icon"
        }
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 1) 
        {
            state "default", label:'', action:"refresh.refresh", icon: "st.secondary.refresh-icon"
        }
		valueTile("MeterReadingDate", "device.MeterReadingDate", width: 2, height: 1) 
        {
            state "val", label: '검침일 : ${currentValue}일'
        }          
        valueTile("SummerSeason", "device.SummerSeason", width: 2, height: 1) 
        {
            state "val", label: '하계시즌일 : ${currentValue}일'
        }      
        valueTile("EtcSeason", "device.EtcSeason", width: 2, height: 1) 
        {
            state "val", label: '계절시즌일 : ${currentValue}일'
        }  
        valueTile("ElectricCharges", "device.ElectricCharges", width: 2, height: 1) 
        {
            state "val", label: '현재요금 : ${currentValue}원'
        }          


        main (["power",  "powerConsumptionStep"])
        details(["power", "ThisMonthEnergy", "powerConsumption", "MeterReadingDate"
        		,"SummerSeason", "EtcSeason", "ElectricCharges"
        		, "reset", "refresh"])
         
    }    
}


def initialize() 
{ 
  log.debug "call initialize" 
}

def handlerMethod() 
{
   log.debug "Event run this month"
   reset() 
}

def reset() 
{
    log.debug "Resetting kWh..."
    sendEvent(name: "resetTotal", value: device.currentState('kwhTotal')?.doubleValue, unit: "kWh")
    sendEvent(name: "ThisMonthEnergy", value: 0, unit: "kWh")
    log.debug "Event registration that runs once a month. - YSB"
    schedule("0 0 0 ${MeterReadingDate.value} 1/1 ? *", handlerMethod) 
    //설정된 매월 검침일 00:00 누적전력 초기화 호출 ,cronmaker 참조
       
}

def parse(String description) 
{
    log.debug "description is $description"  
    def event = zigbee.getEvent(description)

    if (event) 
    {
        log.info event
        if (event.name == "power") 
        {
            if (device.currentState('resetTotal')?.doubleValue == null) 
            {
    			sendEvent(name: "resetTotal", value: 0, unit: "kWh")
            }
            else if (event.cluster == 0x0702 && event.attrId == 0x0400)  
            {
            	event.value = Math.round(event.value/1000)
            	event.unit = "W"
                sendEvent(name: "power", value : Math.round(event.value), unit: "W")            
            }
            /*
            else if (event.cluster == 0x0B04 && event.attrId == 0x050b) 
            {
               	event.value = Math.round(event.value/10)
            	event.unit = "W"
            }
            */
 			else if (event.cluster == 0x0702 && event.attrId == 0x0000) 
            {
                log.debug "Energy_log $descMap.clusterInt" 
                event.name = "powerConsumption"
                event.value = Math.round(event.value/1000000)
                event.unit = "kWh"
                log.info "event outer:$event"
                sendEvent(event)

                if (device.currentState('resetTotal')?.doubleValue == null) 
                {
                   sendEvent(name: "resetTotal", value: 0, unit: "kWh")
                }
                else
                {                  
                   sendEvent(name: "energy", value: Math.round(value), unit: "kWh")
                   def value2 = Math.round(event.value) - device.currentState('resetTotal')?.doubleValue
                   sendEvent(name: "ThisMonthEnergy", value: Math.round(value2), unit: "kWh")
                   log.debug "thismonth $ThisMonthEnergy"
                   sendEvent(name: "kwhTotal", value:  Math.round(event.value) , unit: "kWh", displayed: false)               
                }         
            }            
        } 
        else if (event.name == "energy") 
        {
            event.value = Math.round(event.value/1000000)
            event.unit = "kWh"
            log.info "event outer:$event"
            //sendEvent(event)           
            if (device.currentState('resetTotal')?.doubleValue == null) 
            {
               sendEvent(name: "resetTotal", value: 0, unit: "kWh")
            }
            else
            {
               sendEvent(name: "energy", value: Math.round(event.value), unit: "kWh")
               def value2 = Math.round(event.value) - device.currentState('resetTotal')?.doubleValue
               sendEvent(name: "ThisMonthEnergy", value: Math.round(value2), unit: "kWh")
               sendEvent(name: "kwhTotal", value: Math.round(value), unit: "kWh", displayed: false)               
            }    
        }
    } 
    else  
    {
        List result = []
        def descMap = zigbee.parseDescriptionAsMap(description)
        log.debug "Desc Map: $descMap"
                
        List attrData = [[clusterInt: descMap.clusterInt ,attrInt: descMap.attrInt, value: descMap.value]]
        descMap.additionalAttrs.each 
        {
            attrData << [clusterInt: descMap.clusterInt, attrInt: it.attrInt, value: it.value]
        }
        attrData.each 
        {
                def map = [:]
                             
                if (it.clusterInt == 0x0702 && it.attrInt == 0x0400) 
                {
                        log.debug "meter"
                        map.name = "power"
                        map.value = Math.round(zigbee.convertHexToInt(it.value)/1000)
                        map.unit = "W"
                       
                }
                /*
                if (it.clusterInt == 0x0b04 && it.attrInt == 0x050b) 
                {
                        log.debug "meter"
                        map.name = "power"
                        map.value = Math.round(zigbee.convertHexToInt(it.value)/10)
                        map.unit = "W"
                } 
                */
                if (it.clusterInt == 0x0702 && it.attrInt == 0x0000) 
                {
                        log.debug "energy"
                        map.name = "powerConsumption"
                        map.value = Math.round(zigbee.convertHexToInt(it.value)/1000000)
                        map.unit = "kWh"

                        if (device.currentState('resetTotal')?.doubleValue == null) 
                        {
                           sendEvent(name: "resetTotal", value: 0, unit: "kWh")
                        }
                        else
                        {
                           sendEvent(name: "energy", value:  Math.round(zigbee.convertHexToInt(it.value)/1000000), unit: "kWh")
                           def value2 = Math.round(zigbee.convertHexToInt(it.value)/1000000) - device.currentState('resetTotal')?.doubleValue
                           sendEvent(name: "ThisMonthEnergy", value: Math.round(value2), unit: "kWh")
                           sendEvent(name: "kwhTotal", value:Math.round(zigbee.convertHexToInt(it.value)/1000000), unit: "kWh", displayed: false)               
                        }                             
                }          
                if (map) 
                {                 
                        result << sendEvent(map)
                }
                log.debug "Parse returned $map"
        }
        return result      
    }
/*
주택 3KW 이하 저전압기준

[기타계절] 1~6 , 9~12
·1단계 : 200kWh           × 93.3원  
·2단계 : 201kWh ~ 400KwH  × 187.9원 
·3단계 : 401kWh           × 280.6원 

[하계] 7~8
·1단계 : 300kWh             × 93.3원 
·2단계 : 301kWh ~ 450kWh    × 187.9원
·3단계 : 451kWh             × 280.6원 

 공통 누진4단계 : 1001kWh 이상 x 709.5원 

[기본료]
누진1단계 기본료 910원     ~200K
누진2단계 기본료 1600원  201K~400
누진3단계 기본료 7300원  401K~

[총계산] 
전기세           = (기본료+전기세)
부가세           = 전기세 x 0.1  
전력산업기반기금 = 전기세 x 0.037 
총액 = 전기세 + 부가세 + 전력산업기반기금

[6~7] or [8~9] 월 경우
하계/기타계절 요금계산법 = 누진단계x계절일수/30 + 누진단계x하계일수/30
*/

def day_list = [0,31,28,31,30,31,30,31,31,30,31,30,31] // 1~12
def other_step = [200, 400, 1000]	// 기타계절 구간
def other_rate = [93.3, 187.9, 280.6, 709.5]	// 기타계절 구간 요율
def other_fare = [other_step[0]*other_rate[0], other_step[0]*other_rate[0] + (other_step[1]-other_step[0])*other_rate[1], other_step[0]*other_rate[0] + (other_step[1]-other_step[0])*other_rate[1] + (other_step[2]-other_step[1])*other_rate[2]]	// 기타계절 구간별 최대 요금
def summer_step = [300, 450, 1000]	// 하계 구간
def summer_rate = [93.3, 187.9, 280.6, 709.5]	// 하계 구간 요율
def summer_fare = [summer_step[0]*summer_rate[0], summer_step[0]*summer_rate[0] + (summer_step[1]-summer_step[0])*summer_rate[1], summer_step[0]*summer_rate[0] + (summer_step[1]-summer_step[0])*summer_rate[1] + (summer_step[2]-summer_step[1])*summer_rate[2]]	// 하계 구간별 최대 요금
def base_fare = [910, 1600, 7300, 7300]	// 구간별 기본요금
def vat_rate = 0.1	// 부가가치세
def misc_rate = 0.037	// 전력산업기반기금
def special_energy = 200	// 필수사용량 보장공제 최대 사용량 요건
def special_withdraw = 1000	// 필수사용량 보장공제 금액
def special_max = 2000	// 필수사용량 보장공제 최대 공제금액

def month_energy = device.currentState('ThisMonthEnergy')?.doubleValue
def cal_month = new Date().format("MM", location.timeZone)
def cal_day = new Date().format("dd", location.timeZone)
def this_month, this_day

    if( (cal_day.toInteger()) < MeterReadingDate.toInteger() )//MeterReadingDate.value)
    {    
       this_day = day_list[cal_month.toInteger()-1] //이번달 일수
       this_month = cal_month.toInteger()-1 //기준 달
    }
    else
    {
       this_day = day_list[cal_month.toInteger()]
       this_month = cal_month.toInteger() //기준 달
    }

	def temp_cor0 = -Math.floor(month_energy*5.0)	// 환경비용차감
	def temp_cor1 = Math.floor(month_energy*5.3)	// 기후환경비용
	def temp_cor2 = -Math.floor(month_energy*0.3)	// 연료비조정액
	   
    if(this_month == 7) // 7~8
    {     
       sendEvent(name: 'SummerSeason', value: this_day ,unit: "일") 
       sendEvent(name: 'EtcSeason', value: 0,unit: "일")        
	   
       if(month_energy <= summer_step[0])
       {     
          def temp_charge = Math.floor(base_fare[0]+month_energy*summer_rate[0])
		  temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
		  if(month_energy <= special_energy) {	// 필수사용량 보장공제
			  def special = temp_charge - special_withdraw
			  if (special < special_max) {
				temp_charge = temp_charge - special
			  } else {
				temp_charge = temp_charge - special_max
			  }
		  }
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계1 단계")     
       }
       else if(month_energy <= summer_step[1])
       {
          def temp_charge = Math.floor(base_fare[1]+summer_fare[0]+((month_energy-summer_step[0])*summer_rate[1]))
		  temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2)  , unit: "원")      
          sendEvent(name: 'powerConsumptionStep', value: "하계2 단계")
       }
       else if(month_energy <= summer_step[2])
       {
          def temp_charge = Math.floor(base_fare[2]+summer_fare[1]+((month_energy-summer_step[1])*summer_rate[2]))
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" )      
          sendEvent(name: 'powerConsumptionStep', value: "하계3 단계")       
       }
       else
       {
          def temp_charge = Math.floor(base_fare[3]+summer_fare[2]+((month_energy-summer_step[2])*summer_rate[3]))
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" )      
          sendEvent(name: 'powerConsumptionStep', value: "슈퍼누진4단계")          
       }
    }
    else if( (this_month == 6) || (this_month == 8)) //6~7  8~9 
    {
         def season_etc = 0.0
         def season_summmer =  0.0
         def season_etc_energy = 0
         def season_summer_energy = 0     

       if(this_month == 8) //8~9
       {
         sendEvent(name: 'SummerSeason', value: (this_day - MeterReadingDate.toInteger()) ,unit: "일")         
 		 sendEvent(name: 'EtcSeason', value: MeterReadingDate.toInteger() ,unit: "일")
         season_etc = MeterReadingDate.toInteger()/this_day
         season_summmer = 1.0 - season_etc
         season_etc_energy = month_energy*season_etc
         season_summer_energy = month_energy - season_etc_energy
       }
       else //6~7
       {
         sendEvent(name: 'SummerSeason', value: MeterReadingDate.toInteger() ,unit: "일")
         sendEvent(name: 'EtcSeason', value: (this_day - MeterReadingDate.toInteger()) ,unit: "일")   
         season_summmer =  MeterReadingDate.toInteger()/this_day
		 season_etc = 1.0 - season_summer
         season_summer_energy = month_energy*season_summer
		 season_etc_energy = month_energy - season_summmer_energy
		}

       if(month_energy <= other_step[0])
       {
          def temp_charge = Math.floor(base_fare[0]+month_energy*other_rate[0]) 
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
		  if(month_energy <= special_energy) {	// 필수사용량 보장공제
			  def special = temp_charge - special_withdraw
			  if (special < special_max) {
				temp_charge = temp_charge - special
			  } else {
				temp_charge = temp_charge - special_max
			  }
		  }
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계1/일반1 단계")          
       }
       else if(month_energy <= summer_step[0])
       {
		  def basic_fare = Math.floor(base_fare[1]*season_etc + base_fare[0]*season_summer)
          def temp_charge = Math.floor(                             basic_fare + //기본 요금
          						      (other_step[0]*season_etc*other_rate[0]) + //일반 누진1 요금
            ((season_etc_energy - (other_step[0]*season_etc)) * other_rate[1]) + //일반 누진2 요금
                                        (season_summer_energy*summer_rate[0]))   //하계 누진1 요금
          
		  temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2), unit: "원"  ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계1/일반2 단계")         
       }
       else if(month_energy <= other_step[1])
       {
          def temp_charge = Math.floor(                                     base_fare[1] + //기본 요금
          				   		                (other_step[0]*season_etc*other_rate[0]) + //일반 누진1 요금
                      ((season_etc_energy - (other_step[0]*season_etc)) * other_rate[1]) + //일반 누진2 요금
                                          (summer_step[0]*season_summmer*summer_rate[0]) + //하계 누진1 요금
            ((season_summer_energy - (summer_step[0]*season_summmer)) * summer_rate[1]))   //하계 누진2 요금
          
          
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2), unit: "원"  ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계2/일반2 단계")          
       }
       else if(month_energy <= summer_step[1])
       {
		  def basic_fare = Math.floor(base_fare[2]*season_etc + base_fare[1]*season_summer)
          def temp_charge = Math.floor(                                       basic_fare + //기본 요금
          						                (other_step[0]*season_etc*other_rate[0]) + //일반 누진1 요금
                                ((other_step[1]-other_step[0])*season_etc*other_rate[1]) + //일반 누진2 요금
                      ((season_etc_energy - (other_step[1]*season_etc)) * other_rate[2]) + //일반 누진3 요금          
                                          (summer_step[0]*season_summmer*summer_rate[0]) + //하계 누진1 요금
            ((season_summer_energy - (summer_step[0]*season_summmer)) * summer_rate[1]))   //하계 누진2 요금
  
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2), unit: "원"  ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계2/일반3 단계")          
       }
       else if(month_energy <= other_step[2])	
       {
		   // other_step[2]와 summer_step[2]가 같은 경우에만 수식이 적용됨. 추후 이 값들이 달라진다면 여기와 아래 "else"는 달라져야 함
          def temp_charge = Math.floor(                                     base_fare[2] + //기본 요금
          						                (other_step[0]*season_etc*other_rate[0]) + //일반 누진1 요금
                                ((other_step[1]-other_step[0])*season_etc*other_rate[1]) + //일반 누진2 요금
                      ((season_etc_energy - (other_step[1]*season_etc)) * other_rate[2]) + //일반 누진3 요금          
                                          (summer_step[0]*season_summmer*summer_rate[0]) + //하계 누진1 요금
                        ((summer_step[1]-summer_step[0])*season_summmer* summer_rate[1]) + //하계 누진2 요금                                      
            ((season_summer_energy - (summer_step[1]*season_summmer)) * summer_rate[2])) //하계 누진3 요금
          
		  temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" ) 
          sendEvent(name: 'powerConsumptionStep', value: "하계3/일반3 단계")        
       }
	   else	// 슈퍼유저
	   {
		   def temp_charge = Math.floor(                                    base_fare[3] + //기본 요금
          						                (other_step[0]*season_etc*other_rate[0]) + //일반 누진1 요금
                                ((other_step[1]-other_step[0])*season_etc*other_rate[1]) + //일반 누진2 요금
                      ((season_etc_energy - (other_step[1]*season_etc)) * other_rate[2]) + //일반 누진3 요금          
                                          (summer_step[0]*season_summmer*summer_rate[0]) + //하계 누진1 요금
                        ((summer_step[1]-summer_step[0])*season_summmer* summer_rate[1]) + //하계 누진2 요금                                      
					    ((summer_step[2]-summer_step[1])*season_summmer* summer_rate[2]) + //하계 누진3 요금                                      
            ((season_summer_energy - (summer_step[2]*season_summmer)) * summer_rate[3])) //슈퍼유저 요금
	   
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" ) 
          sendEvent(name: 'powerConsumptionStep', value: "슈퍼누진4/일반3 단계")        
	   }


    }
    else //1~6 , 9~12
    {
       sendEvent(name: 'SummerSeason', value: 0,unit: "일") 
       sendEvent(name: 'EtcSeason', value: this_day,unit: "일")
       
       if(month_energy <= other_step[0])
       {
          def temp_charge = Math.floor(base_fare[0]+month_energy*other_rate[0])
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
		  if(month_energy <= special_energy) {	// 필수사용량 보장공제
			  def special = temp_charge - special_withdraw
			  if (special < special_max) {
				temp_charge = temp_charge - special
			  } else {
				temp_charge = temp_charge - special_max
			  }
		  }
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10

          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" ) 
          sendEvent(name: 'powerConsumptionStep', value: "일반1 단계")       
       }
       else if(month_energy <= other_step[1])
       {
          def temp_charge = Math.floor(base_fare[1]+other_fare[0]+((month_energy-other_step[0])*other_rate[1]))
		  temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2) , unit: "원" )      
          sendEvent(name: 'powerConsumptionStep', value: "일반2 단계")
       }
       else if(month_energy <= other_step[2])
       {
          def temp_charge = Math.floor(base_fare[2]+other_fare[1]+((month_energy-other_step[1])*other_rate[2]))
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2), unit: "원"  )      
          sendEvent(name: 'powerConsumptionStep', value: "일반3 단계")        
       }
       else
       {
          def temp_charge = Math.floor(base_fare[3]+other_fare[2]+((month_energy-other_step[2])*other_rate[3]))
          temp_charge = temp_charge + temp_cor0 + temp_cor1 + temp_cor2
          def temp_tax1 = Math.round(temp_charge*vat_rate)
          def temp_tax2 = Math.floor(temp_charge*misc_rate/10)*10
          sendEvent(name: 'ElectricCharges', value: Math.round(temp_charge+temp_tax1+temp_tax2)  , unit: "원")      
          sendEvent(name: 'powerConsumptionStep', value: "슈퍼누진4 단계")          
       } 
    }
 
//log.debug Integer.parseInt(device.currentState('Electric_charges')?.doubleValue,1)

	sendCharge(device.currentState('ElectricCharges')?.doubleValue, device.currentState('ThisMonthEnergy')?.doubleValue )
   
}

def updated() 
{
    sendEvent(name: 'MeterReadingDate', value: MeterReadingDate+" 일" )//, unit:"일") 
    sendEvent(name: "resetTotal", value: LastMonthWatt.toInteger(), unit: "kWh")    
    log.debug "Event registration that runs once a month. - YSB"
    schedule("0 0 0 ${MeterReadingDate.value} 1/1 ? *", handlerMethod) 
    //설정된 매월 검침일 00:00 누적전력 초기화 호출 ,cronmaker 참조    
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() 
{
    return refresh()
}

def refresh() {
    log.debug "refresh "
    zigbee.electricMeasurementPowerRefresh() +
           zigbee.simpleMeteringPowerRefresh()
}
def installed() 
{
    log.debug "Installed"
    sendEvent(name: "resetTotal", value: 0, unit: "kWh")
}
private void createChild() 
{
      log.debug "Creating child"
      def child = addChildDevice("smartthings","Zigbee Power Meter",
                                   "${device.deviceNetworkId}:${1}", 
                                                       device.hubId,
                                              [completedSetup: true, 
                                                  label: "전기세"])
}

private channelNumber(String dni) 
{
   dni.split(":")[-1] as Integer
}

private sendCharge(Double Charger,Double ThisMonthEnergy ) 
{
   def descriptionText =  "실시간 전기요금 사용량"
   def child = childDevices?.find { channelNumber(it.deviceNetworkId) == 1 }
  
   if (child)
   {
      child?.sendEvent([name: "energy", value: ThisMonthEnergy , data: [1: 1], descriptionText: descriptionText, isStateChange: true])
      child?.sendEvent([name: "power", value: Charger, data: [1: 1], descriptionText: descriptionText, isStateChange: true])     
   }
   else
   {
      log.debug "Child device not found!"
      createChild()      
   }  
}
def configure() 
{
    // this device will send instantaneous demand and current summation delivered every 1 minute
    sendEvent(name: "checkInterval", value: 2 * 60 + 10 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    log.debug "Configuring Reporting"
    return refresh() +
           zigbee.simpleMeteringPowerConfig() +
           zigbee.electricMeasurementPowerConfig()
}
