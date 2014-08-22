[womai](http://www.instructables.com/member/womai/)

Two ideas to make the code more efficient (run faster):

The calculation

    ADCCounter = ( ADCCounter + 1 ) % ADCBUFFERSIZE;

involves an integer division which tends to be time consuming (at least on a Microchip PIC - I do not know Atmel as well). Instead, try

    if (++ADCCounter >= ADCBUFFERSIZE) ADCCounter = 0;

Second, you can completely avoid the time required to evaluate

    if(wait)

and live without the wait variable if you simply set stopIndex to a value that the counter never reaches, as long as you aren't yet in the post-trigger phase. I.e. during initialization (when starting a new sweep) set

    stopIndex = ADCBUFFERSIZE + 1;

and when the trigger event happens then just do as you did so far, but without the boolean wait variable:

    ISR(ANALOG_COMP_vect)
    {
        // Disable Analog Comparator interrupt
        cbi( ACSR,ACIE );
        
        // Turn on errorPin
        //digitalWrite( errorPin, HIGH );
        sbi( PORTB, PORTB5 );
        
        stopIndex = ( ADCCounter + waitDuration ) % ADCBUFFERSIZE;
    }

and the ADC ISR routine becomes

    ISR(ADC_vect)
    {
        if (++ADCCounter >= ADCBUFFERSIZE) ADCCounter = 0;
        
        if ( stopIndex == ADCCounter )
        {
            cbi( ADCSRA, ADEN );
            freeze = true;
        }
    }
