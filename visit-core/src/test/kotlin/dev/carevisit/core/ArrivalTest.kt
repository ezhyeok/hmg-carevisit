package dev.carevisit.core

import kotlin.test.*

class ArrivalTest {
    private val a=Hospital("가상 A병원",address="가상 A주소")
    private val b=Hospital("가상 B병원",address="가상 B주소")
    private fun started(): VisitMachine = VisitMachine(Archive(consent=Consent(1)),1).also {
        it.handle(Event.Vehicle(Gear.P,0.0,false,false),1,0)
        it.handle(Event.Destination(a),1,0)
    }
    @Test fun wrongNameOrAddressCannotArriveAtCurrentVisit() {
        for(event in listOf(Event.NavigationArrived(b.name,b.address),Event.NavigationArrived(a.name,b.address),Event.NavigationArrived("",""))) {
            val m=started();m.handle(event,2,1);assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
        }
    }
    @Test fun matchingArrivalKeepsDestinationAddressAndRequiresP() {
        val m=started();m.handle(Event.Vehicle(Gear.D,5.0,true,false),2,1)
        m.handle(Event.NavigationArrived(a.name,a.address),3,2);assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
        m.handle(Event.Vehicle(Gear.P,0.0,false,false),4,3)
        assertEquals(Phase.AT_HOSPITAL,m.archive.session.phase);assertEquals(a.address,m.archive.session.visit!!.hospital.address)
    }
    @Test fun destinationChangeClearsAnArrivalWaitingForP() {
        val m=started();m.handle(Event.Vehicle(Gear.D,5.0,true,false),2,1)
        m.handle(Event.NavigationArrived(a.name,a.address),3,2)
        m.handle(Event.Destination(b),4,3);m.handle(Event.Vehicle(Gear.P,0.0,false,false),5,4)
        assertEquals(Phase.WAIT_SAFE,m.archive.session.phase);assertEquals(b,m.archive.session.pendingHospital)
        m.handle(Event.NavigationArrived(a.name,a.address),6,5);assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
    }
    @Test fun cancellationClearsCandidateAndLateArrivalCannotReviveIt() {
        val m=started();m.handle(Event.Vehicle(Gear.D,5.0,true,false),2,1)
        m.handle(Event.NavigationArrived(a.name,a.address),3,2);m.handle(Event.NavigationCleared,4,3)
        m.handle(Event.NavigationArrived(a.name,a.address),5,4);m.handle(Event.Vehicle(Gear.P,0.0,false,false),6,5)
        assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
    }
    @Test fun staleArrivalExpiresBeforeParking() {
        val m=started();m.handle(Event.Vehicle(Gear.D,5.0,true,false),2,1)
        m.handle(Event.NavigationArrived(a.name,a.address),3,2)
        m.handle(Event.Vehicle(Gear.P,0.0,false,false),31_004,31_003)
        assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
    }
    @Test fun nonHospitalDestinationInvalidatesOldHospitalArrival() {
        val m=started();m.handle(Event.Destination(Hospital("가상 공원")),2,1)
        m.handle(Event.NavigationArrived(a.name,a.address),3,2)
        assertEquals(Phase.WAIT_SAFE,m.archive.session.phase);assertNull(m.archive.session.pendingHospital)
    }
    @Test fun dismissalKeepsTheOldVisitAndRepeatedPollDoesNotReprompt() {
        val m=started();val id=m.archive.session.visit!!.id
        m.handle(Event.Destination(b),2,1);m.handle(Event.DismissDestination,3,2)
        m.handle(Event.Destination(b),4,3)
        assertNull(m.archive.session.pendingHospital);assertEquals(id,m.archive.session.visit!!.id)
        m.handle(Event.NavigationArrived(b.name,b.address),5,4);assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
    }
    @Test fun hospitalSwitchRequiresPAndKeepsOldMemoThenRejectsLateOldArrival() {
        val m=started();m.handle(Event.PrepareParked,1,0)
        m.handle(Event.Answer(m.archive.session.prompt!!.id,"네"),1,0)
        m.handle(Event.Answer(m.archive.session.prompt!!.id,"무릎이 아프"),1,0)
        val old=m.archive.session.visit!!
        m.handle(Event.Vehicle(Gear.D,5.0,true,false),2,1);m.handle(Event.Destination(b),3,2)
        m.handle(Event.ConfirmDestination,3,2);assertEquals(old.id,m.archive.session.visit?.id)
        m.handle(Event.Vehicle(Gear.P,0.0,false,false),4,3);m.handle(Event.ConfirmDestination,4,3)
        assertEquals(b,m.archive.session.visit?.hospital);assertNotEquals(old.id,m.archive.session.visit?.id)
        assertEquals(old,m.archive.visits.single());assertNull(m.archive.session.pendingHospital)
        m.handle(Event.NavigationArrived(a.name,a.address),5,4);assertEquals(Phase.WAIT_SAFE,m.archive.session.phase)
        m.handle(Event.NavigationArrived(b.name,b.address),6,5);assertEquals(Phase.AT_HOSPITAL,m.archive.session.phase)
    }
}
