package io.katharsis.dispatcher.controller.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.katharsis.dispatcher.controller.HttpMethod;
import io.katharsis.queryParams.QueryParams;
import io.katharsis.repository.RepositoryMethodParameterProvider;
import io.katharsis.repository.ResourceRepository;
import io.katharsis.request.dto.DataBody;
import io.katharsis.request.dto.RequestBody;
import io.katharsis.request.path.JsonPath;
import io.katharsis.request.path.ResourcePath;
import io.katharsis.resource.exception.RequestBodyException;
import io.katharsis.resource.exception.RequestBodyNotFoundException;
import io.katharsis.resource.exception.ResourceNotFoundException;
import io.katharsis.resource.registry.RegistryEntry;
import io.katharsis.resource.registry.ResourceRegistry;
import io.katharsis.response.BaseResponse;
import io.katharsis.response.LinksInformation;
import io.katharsis.response.MetaInformation;
import io.katharsis.response.ResourceResponse;
import io.katharsis.utils.parser.TypeParser;

import java.io.Serializable;
import java.util.Collections;

public class ResourcePatch extends ResourceUpsert {

    public ResourcePatch(ResourceRegistry resourceRegistry, TypeParser typeParser, @SuppressWarnings("SameParameterValue") ObjectMapper objectMapper) {
        super(resourceRegistry, typeParser, objectMapper);
    }

    @Override
    public boolean isAcceptable(JsonPath jsonPath, String requestType) {
        return !jsonPath.isCollection() &&
                jsonPath instanceof ResourcePath &&
                HttpMethod.PATCH.name().equals(requestType);
    }

    @Override
    public BaseResponse<?> handle(JsonPath jsonPath, QueryParams queryParams,
                                  RepositoryMethodParameterProvider parameterProvider, RequestBody requestBody) throws Exception {

        String resourceEndpointName = jsonPath.getResourceName();
        RegistryEntry endpointRegistryEntry = resourceRegistry.getEntry(resourceEndpointName);
        if (endpointRegistryEntry == null) {
            throw new ResourceNotFoundException(resourceEndpointName);
        }
        if (requestBody == null) {
            throw new RequestBodyNotFoundException(HttpMethod.PATCH, resourceEndpointName);
        }
        if (requestBody.isMultiple()) {
            throw new RequestBodyException(HttpMethod.PATCH, resourceEndpointName, "Multiple data in body");
        }

        String idString = jsonPath.getIds().getIds().get(0);

        DataBody dataBody = requestBody.getSingleData();
        if (dataBody == null) {
            throw new RequestBodyException(HttpMethod.POST, resourceEndpointName, "No data field in the body.");
        }
        RegistryEntry bodyRegistryEntry = resourceRegistry.getEntry(dataBody.getType());
        verifyTypes(HttpMethod.PATCH, resourceEndpointName, endpointRegistryEntry, bodyRegistryEntry);

        Class<?> type = bodyRegistryEntry
            .getResourceInformation()
            .getIdField()
            .getType();
        Serializable resourceId = typeParser.parse(idString, (Class<? extends Serializable>) type);

        ResourceRepository resourceRepository = endpointRegistryEntry.getResourceRepository(parameterProvider);
        @SuppressWarnings("unchecked")
        Object resource = resourceRepository.findOne(resourceId, queryParams);


        setAttributes(dataBody, resource, bodyRegistryEntry.getResourceInformation());
        setRelations(resource, bodyRegistryEntry, dataBody, queryParams, parameterProvider);
        Object savedResource = resourceRepository.save(resource);

        MetaInformation metaInformation =
            getMetaInformation(resourceRepository, Collections.singletonList(savedResource), queryParams);
        LinksInformation linksInformation =
            getLinksInformation(resourceRepository, Collections.singletonList(savedResource), queryParams);

        return new ResourceResponse(savedResource, jsonPath, queryParams, metaInformation, linksInformation);
    }
}
