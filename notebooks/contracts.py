from ibapi.contract import Contract

class Contracts:

    map = dict()

    def info(self):
        return f"map: {self.map} "

    def add(contract: Contract):
        map[contract.symbol] = contract

    def run(self):
        eurusd = Contract()
        eurusd.symbol = 'EUR'
        eurusd.secType = 'CASH'
        eurusd.exchange = 'IDEALPRO'
        eurusd.currency = 'USD'

        eurzar = Contract()
        eurzar.symbol = 'EUR'
        eurzar.secType = 'CASH'
        eurzar.exchange = 'IDEALPRO'
        eurzar.currency = 'ZAR'

        qqq = Contract()
        qqq.symbol = 'QQQ'
        qqq.secType = 'CASH'
        qqq.exchange = 'SMART'
        qqq.currency = 'USD'

        es = Contract()
        es.symbol = 'ES'
        es.secType = 'FUT'
        es.exchange = 'CME'
        es.currency = 'USD'
        es.lastTradeDateOrContractMonth = '20230616'
        
        add(eurusd)
        add(eurzar)
        add(qqq)
        add(es)
