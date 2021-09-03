// Copyright (c) 2021 All Rights Reserved.

import  ballerina/http;


public isolated client class Client {
    final http:Client clientEp;
    # Gets invoked to initialize the `connector`.
    #
    # + clientConfig - The configurations to be used when initializing the `connector`
    # + serviceUrl - URL of the target service
    # + return - An error if connector initialization failed
    public isolated function init(http:ClientConfiguration clientConfig =  {}, string serviceUrl = "https://petstore.swagger.io:443/v2") returns error? {
        http:Client httpEp = check new (serviceUrl, clientConfig);
        self.clientEp = httpEp;
    }
    # List all pets
    #
    # + 'limit - How many items to return at one time (max 100)
    # + return - An paged array of pets
    remote isolated function listPets(int? 'limit = ()) returns Pets|error {
        string  path = string `/pets`;
        map<anydata> queryParam = {"limit": 'limit};
        path = path + check getPathForQueryParam(queryParam);
        Pets response = check self.clientEp-> get(path, targetType = Pets);
        return response;
    }
    # Create a pet
    #
    # + return - Null response
    remote isolated function  createPet() returns http:Response|error {
        string  path = string `/pets`;
        http:Request request = new;
        //TODO: Update the request as needed;
        http:Response response = check self.clientEp-> post(path, request, targetType = http:Response);
        return response;
    }
    # Info for a specific pet
    #
    # + petId - The id of the pet to retrieve
    # + return - Expected response to a valid request
    remote isolated function showPetById(string petId) returns Pets|error {
        string  path = string `/pets/${petId}`;
        Pets response = check self.clientEp-> get(path, targetType = Pets);
        return response;
    }
}


