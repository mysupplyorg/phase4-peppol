package com.mysupply.phase4;

public interface ICountryCodeMapper
{
    /// Map the endpoint type and value to a country code.
    String mapCountryCode(String endpointType, String endpointValue);
}