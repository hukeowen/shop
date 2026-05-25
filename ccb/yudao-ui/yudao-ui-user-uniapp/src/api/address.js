import { get, post, put, del } from '@/utils/request.js';

export const listAddresses = () => get('/app-api/member/address/list');
export const getDefaultAddress = () => get('/app-api/member/address/get-default');
export const createAddress = (body) => post('/app-api/member/address/create', body);
export const updateAddress = (body) => put('/app-api/member/address/update', body);
export const deleteAddress = (id) => del(`/app-api/member/address/delete?id=${id}`);
