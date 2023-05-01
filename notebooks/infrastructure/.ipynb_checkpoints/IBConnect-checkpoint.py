from ibapi.client import EClient
from ibapi.wrapper import EWrapper  
from ibapi.contract import Contract

import threading
import time

class IBConnect(EWrapper, EClient):
    def __init__(self):
        EClient.__init__(self, self)
        self.data = []

    def tickPrice(self, reqId, tickType, price, attrib):
        if tickType == 2 and reqId == 1:
            print(tickType, ': The current ask price is: ', price)
    
    def historicalData(self, reqId, bar):
        self.data.append([bar.date, bar.open, bar.high, bar.low, bar.close])

    def symbolSamples(self, reqId: int,
                      contractDescriptions: ListOfContractDescription):
        super().symbolSamples(reqId, contractDescriptions)
        print("Symbol Samples. Request Id: ", reqId)
        for contractDescription in contractDescriptions:
            derivSecTypes = ""
            for derivSecType in contractDescription.derivativeSecTypes:
                derivSecTypes += " "
                derivSecTypes += derivSecType
                print("Contract: conId:%s, symbol:%s, secType:%s primExchange:%s, "
                      "currency:%s, derivativeSecTypes:%s, description:%s, issuerId:%s" % (
                      contractDescription.contract.conId,
                      contractDescription.contract.symbol,
                      contractDescription.contract.secType,
                      contractDescription.contract.primaryExchange,
                      contractDescription.contract.currency, derivSecTypes,
                      contractDescription.contract.description,
                      contractDescription.contract.issuerId))